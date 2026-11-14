# Publishing on Google Play

## One-time setup
1. Create a Google Play Console account at play.google.com/console. It costs a one-time 25 USD fee and needs ID verification. New personal accounts must run a closed test with at least 12 testers for 14 days before they can publish to production.
2. Create the app: name Aread, default language English, app, free.

## Signing key
1. On any computer or in a cloud shell run: `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
2. Convert it: `base64 -w0 upload.jks`
3. In the GitHub repo open Settings, Secrets and variables, Actions and add: `KEYSTORE_BASE64` (the text from step 2), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (upload), `KEY_PASSWORD`.
4. Keep `upload.jks` and the passwords somewhere safe. Never commit them.

## Build
Open the Actions tab, choose Release bundle, Run workflow. Download `aread-release-aab` when it finishes.

## Upload
1. Play Console, Testing, Closed testing, Create release, accept Play App Signing, upload the .aab.
2. Add your friends' Gmail addresses to a tester list and share the opt-in link.
3. After 14 days with 12 or more testers, apply for production access.

## Store listing needs
App icon 512x512, feature graphic 1024x500, at least 2 phone screenshots, short and full description, privacy policy URL, content rating questionnaire, data safety form, target audience (13+ is simplest because the app has accounts).

## Policy declarations you must expect
- Accessibility service: explain it is used only to detect which app is in the foreground so Aread can pause distracting apps during a session the user started.
- Call screening, usage access, device admin, display over other apps, and query all packages each need a clear reason in the declaration forms.
- Data safety: collects name, phone or email, school details and study minutes, encrypted in transit, deletable on request. Add an in-app or web way to request deletion.
- Paying for Pro credits inside the app must use Google Play Billing. Codes sold outside the app are fine for a first version, but Play can ask you to move them to Billing.
