# Play Console Upload Steps

Follow these steps to upload your generated Android App Bundle (.aab) to Google Play Console.

## 1. Create the App
- Open Google Play Console.
- Click **Create app**.
- Set **App name**: `OverRead`.
- Set **Default language**: English.
- Select App type: **App** (not Game).
- Select Pricing: **Free** or **Paid**.
- Accept the Developer Program Policies.

## 2. App Content Declarations
Complete the tasks in the "App content" section:
- **Privacy Policy**: Provide the public URL to your hosted privacy policy.
- **Ads**: Declare if the app contains ads (No).
- **App Access**: Declare if any parts of the app are restricted.
- **Content Rating**: Complete the questionnaire.
- **Target Audience**: Select your target age groups (e.g., 18+ depending on the type of unselectable content).
- **News Apps**: Declare that it is not a news app.
- **Data Safety**:
  - Does the app collect or share any user data? **No** (screenshots are local, text is not saved).
  - Declare the use of Internet for downloading ML Kit Language models without transmitting user data.
- **Advertising ID**: Declare if required (No).

## 3. Store Listing
- **App Category**: E.g., Tools or Productivity.
- **Contact Details**: Add support email.
- Set your Short Description, Full Description, and upload all prepared graphic assets (Icon, Feature Graphic, Screenshots).

## 4. Setup Testing Tracks
- Go to **Testing > Closed testing** or **Internal testing**.
- Create a new release track.
- Create a new list of testers or add their email addresses. Provide them a joining link or invite.

## 5. Upload AAB & Release Notes
- Click **Create new release**.
- Opt-in to **Play App Signing**.
- Upload your `app-release.aab` generated from `./gradlew bundleRelease`.
- Review the release name and add Release Notes.
- Click **Save**, then **Review release**.

## 6. Submit for Review
- Resolve any warnings.
- Click **Start rollout to Closed testing**.
