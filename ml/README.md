# ShieldXMobile SMS model training

ShieldXMobile's on-device classifier is trained as a three-class text classifier:
- `ham`: legitimate / non-spam
- `spam`: unsolicited or promotional spam
- `smishing`: SMS phishing attempts

## Dataset and attribution

Training uses **SMS PHISHING DATASET FOR MACHINE LEARNING AND PATTERN RECOGNITION**, by Sandhya Mishra and Devpriya Soni, Mendeley Data, Version 1, published 20 June 2022.

- DOI: https://doi.org/10.17632/f45bkkt8pr.1
- Dataset page: https://data.mendeley.com/datasets/f45bkkt8pr/1
- License: Creative Commons Attribution 4.0 International (CC BY 4.0)
- Original class counts: 4,844 ham, 489 spam, and 638 smishing messages (5,971 total).

The training workflow downloads a pinned copy of the CSV from the public `MarazMia/SMISH_DT` GitHub mirror at commit `c63f6d58e6deaddd11d0dae7fe9d52c383fd2c7d`. The raw SMS corpus is downloaded only into the CI runner and is **not committed** to this app repository. The generated model and evaluation report retain the dataset citation and license.

## Training and evaluation

The pipeline removes blank rows, normalizes labels, excludes texts with conflicting labels, removes exact duplicate messages before splitting, and makes an 80/20 stratified holdout split. It trains TF-IDF (unigrams + bigrams) with class-weighted Logistic Regression. It reports accuracy, macro-F1, per-class precision/recall/F1 and a confusion matrix.

After evaluating the holdout, it retrains the deployable model on all cleaned rows. The exported JSON is intended for inference directly on the device; no message text is sent to a remote service.

The model is primarily trained from an English-language corpus. The app's English, Hindi and Tamil interface translations do not mean the classifier is equally reliable in those languages. The model is an aid, not a guarantee that a message is safe or fraudulent. Dataset age, collection method, class imbalance and distribution shift may affect real-world performance.

## Rebuild

The `Train SMS model` GitHub Actions workflow runs when the training code or workflow changes, and can also be started manually from the Actions tab. It generates:
- `app/src/main/assets/sms_model.json`
- `ml/generated/evaluation.json`

The workflow commits these generated outputs back to the repository.
