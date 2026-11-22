package com.example.shieldxmobile

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offline TF-IDF + multinomial Logistic Regression classifier.
 *
 * The generated JSON artifact is produced by ml/train_sms_model.py. The
 * heuristic detector remains available as a fallback if the asset is absent.
 * Softmax outputs are model scores, not calibrated real-world probabilities.
 */
object ScamMessageMlClassifier {

    data class Prediction(
        val className: String,
        val confidence: Int,
        val riskScore: Int,
        val label: String,
        val summary: String
    )

    private data class Model(
        val classes: List<String>,
        val vocabulary: Map<String, Int>,
        val idf: DoubleArray,
        val coefficients: Array<DoubleArray>,
        val intercepts: DoubleArray
    )

    @Volatile
    private var cachedModel: Model? = null

    @Volatile
    private var assetUnavailable = false

    fun analyze(context: Context, message: String): Prediction? {
        if (message.isBlank()) return null
        return try {
            val model = loadModel(context.applicationContext) ?: return null
            predict(model, message)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadModel(context: Context): Model? {
        cachedModel?.let { return it }
        if (assetUnavailable) return null

        synchronized(this) {
            cachedModel?.let { return it }
            if (assetUnavailable) return null

            return try {
                val jsonText = context.assets.open("sms_model.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val root = JSONObject(jsonText)
                require(root.optString("format") == "shieldx-tfidf-logreg-v1")

                val classesJson = root.getJSONArray("classes")
                val classes = List(classesJson.length()) { index ->
                    classesJson.getString(index)
                }
                require(classes.size == 3)

                val vocabularyJson = root.getJSONObject("vocabulary")
                val vocabulary = HashMap<String, Int>(vocabularyJson.length())
                val keys = vocabularyJson.keys()
                while (keys.hasNext()) {
                    val token = keys.next()
                    vocabulary[token] = vocabularyJson.getInt(token)
                }

                val idfJson = root.getJSONArray("idf")
                val idf = DoubleArray(idfJson.length()) { index ->
                    idfJson.getDouble(index)
                }
                require(vocabulary.isNotEmpty())
                require(vocabulary.values.all { it in idf.indices })

                val coefficientsJson = root.getJSONArray("coefficients")
                require(coefficientsJson.length() == classes.size)
                val coefficients = Array(classes.size) { classIndex ->
                    val row = coefficientsJson.getJSONArray(classIndex)
                    require(row.length() == idf.size)
                    DoubleArray(row.length()) { featureIndex ->
                        row.getDouble(featureIndex)
                    }
                }

                val interceptsJson = root.getJSONArray("intercepts")
                require(interceptsJson.length() == classes.size)
                val intercepts = DoubleArray(interceptsJson.length()) { index ->
                    interceptsJson.getDouble(index)
                }

                Model(classes, vocabulary, idf, coefficients, intercepts).also {
                    cachedModel = it
                }
            } catch (_: Exception) {
                // The app remains usable with its existing rule-based detector.
                assetUnavailable = true
                null
            }
        }
    }

    private fun predict(model: Model, message: String): Prediction {
        val normalized = Normalizer.normalize(
            message.lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).filterNot { character ->
            val type = Character.getType(character)
            type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
        }

        val matcher = TOKEN_PATTERN.matcher(normalized)
        val tokens = ArrayList<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }

        val termCounts = HashMap<Int, Int>()
        fun addFeature(token: String) {
            val index = model.vocabulary[token] ?: return
            termCounts[index] = (termCounts[index] ?: 0) + 1
        }

        for (index in tokens.indices) {
            addFeature(tokens[index])
            if (index > 0) {
                addFeature(tokens[index - 1] + " " + tokens[index])
            }
        }

        val weights = HashMap<Int, Double>(termCounts.size)
        var squaredNorm = 0.0
        for ((index, count) in termCounts) {
            val tf = 1.0 + ln(count.toDouble())
            val weight = tf * model.idf[index]
            weights[index] = weight
            squaredNorm += weight * weight
        }

        val norm = sqrt(squaredNorm)
        if (norm > 0.0) {
            for (index in weights.keys.toList()) {
                weights[index] = weights.getValue(index) / norm
            }
        }

        val logits = DoubleArray(model.classes.size) { classIndex ->
            var score = model.intercepts[classIndex]
            for ((featureIndex, value) in weights) {
                score += model.coefficients[classIndex][featureIndex] * value
            }
            score
        }

        val maxLogit = logits.maxOrNull() ?: 0.0
        val exponentials = DoubleArray(logits.size) { index ->
            kotlin.math.exp(logits[index] - maxLogit)
        }
        val total = exponentials.sum().coerceAtLeast(1e-12)
        val scores = DoubleArray(exponentials.size) { index ->
            exponentials[index] / total
        }

        val predictedIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        val predictedClass = model.classes[predictedIndex]
        val scoreByClass = model.classes.indices.associate { index ->
            model.classes[index] to scores[index]
        }
        val riskScore = (
            (scoreByClass["spam"] ?: 0.0) +
                (scoreByClass["smishing"] ?: 0.0)
            ).times(100.0).roundToInt().coerceIn(0, 100)
        val confidence = (scores[predictedIndex] * 100.0)
            .roundToInt().coerceIn(0, 100)

        val label = when (predictedClass) {
            "ham" -> "Likely legitimate message pattern"
            "spam" -> "Spam pattern predicted"
            "smishing" -> "Smishing pattern predicted"
            else -> "Unclassified message pattern"
        }
        val summary = "On-device ML prediction: $predictedClass · model score $confidence%. " +
            "Risk score combines the model's spam and smishing scores; it is not a guarantee."

        return Prediction(
            className = predictedClass,
            confidence = confidence,
            riskScore = riskScore,
            label = label,
            summary = summary
        )
    }

    private val TOKEN_PATTERN = java.util.regex.Pattern.compile("[\\p{L}\\p{N}_]{2,}")
}
