# LostAndFound: Smart Item Recovery Platform (Android App)

> 🏆 Final Year Project at Atish Dipankar University of Science & Technology (ADUST). **Placed 2nd at the project defense.**

LostAndFound helps people report lost items, post found items, and get them back to the right owner. It uses on-device AI to describe items automatically and checks claims for fraud before an item is handed over. This repository contains the **Android app**. The companion website is in [LostAndFound-Website](https://github.com/munny1234-web/LostAndFound-Website), and both share the same Firebase backend.

## ✨ Features

- **Report lost and found items** with multi-photo upload
- **Automatic item description** using ML Kit OCR (reads text on items, cards, labels) and image labeling
- **Fraud detection** on ownership claims using Hugging Face semantic similarity
- **Real-time chat** between the finder and the owner
- **Map view** showing where items were lost or found
- **Admin panel** with analytics for moderating posts and claims
- **Companion website** sharing the same live data ([LostAndFound-Website](https://github.com/munny1234-web/LostAndFound-Website))

## 🛠️ Tech Stack

| Part | Technology |
|---|---|
| Android app | Java, XML layouts, Material Design |
| Backend | Firebase Authentication, Firebase Realtime Database |
| AI / ML | Google ML Kit (Text Recognition, Image Labeling), Hugging Face semantic similarity API |

## 📸 Screenshots

| Home | Report Item | Chat | Admin Panel |
|---|---|---|---|
| ![](screenshots/home.png) | ![](screenshots/report.png) | ![](screenshots/chat.png) | ![](screenshots/admin.png) |

## ⚙️ How to Run

1. Clone this repository and open it in Android Studio.
2. Create a Firebase project and enable Authentication and Realtime Database.
3. Download your own `google-services.json` from Firebase and place it in the `app/` folder.
4. Sync Gradle and run the app on an emulator or device.

> `google-services.json` and API keys are not included in this repository for security reasons.

## 👩‍💻 Author

**Jannatul Ferdous Munny**
Supervised by **Mahmudur Rahman Roni**, Associate Professor, Department of CSE, ADUST

[LinkedIn](https://www.linkedin.com/in/jannaul-ferdous-munny-036028226) · ferdousmunny188@gmail.com
