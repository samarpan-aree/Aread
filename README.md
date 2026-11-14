# Aread

Aread is an Android app that helps students study without their phone getting in the way. You start a focus session, and the apps that usually eat your evening (social media, short videos, games) and incoming calls are paused until the timer is done. Between sessions it keeps your streak, plans your day from the pages you are studying, and gives you a tutor to ask when you get stuck.

It was made by Ar Studio for students in Nepal, but nothing in it is specific to one country.

## What it does

- **Focus sessions.** Timer or Pomodoro, with a strict mode that cannot be ended early. In normal mode you can only end a session during its last 30%.
- **App and call blocking.** You choose which apps to pause. Calls from people you have not allowed are screened out during a session.
- **Stays on track.** The app cannot be uninstalled in the middle of a session, and if you disappear for two days it locks distracting apps until you finish one session.
- **Study plan.** Photograph your textbook pages or notes. Aread reads the subject, chapter and topics, estimates how long each needs, and builds a fresh plan every day. Photos that are not study material are rejected.
- **Medha, the tutor.** Medha does not hand over answers. She asks what you have tried and points to the idea you need. If you are still stuck she asks for a photo of your working and shows you which step went wrong. Medha Pro thinks harder and explains more, and is paid for with credits instead of a subscription.
- **Streaks, weekly chart and leaderboard.** Compare minutes with other students at your school and beyond.
- **Screen time.** See where your day went, and how much of it was distraction.
- **Reminders and alerts.** A few well-timed nudges a day, and a sound when your session ends.
- **Exam countdown, themes, and your own background photo.**
- **Privacy.** Usage data stays on the phone. Every permission is explained on its own screen before you grant it.

## How it is built

- Android app in Kotlin with Jetpack Compose, in `app/`.
- Backend on Supabase in `supabase/`: Postgres with row level security, email, Google and phone sign-in, file storage for textbook pages, and edge functions for the tutor and page reading.
- Secrets such as the tutor service key live only in the Supabase project. The app never holds them.

## Building

1. Copy `secrets.properties.example` to `secrets.properties` and fill in your Supabase URL and public key.
2. Run the SQL files in `supabase/migrations` in order.
3. Deploy the functions in `supabase/functions` and add the `GEMINI_API_KEY` secret to the project.
4. `gradle :app:assembleDebug`

The GitHub workflow in `.github/workflows/build.yml` does step 4 on every push and attaches the APK to a release, so you can build without a computer. `release.yml` builds a signed bundle for the Play Store. See `docs/PLAY_STORE.md`.

## Admin

Tap the version number in Settings several times to open the hidden admin panel (only for accounts marked as admin in the database). From there you can see user counts and create Pro credit codes.
