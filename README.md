# Song Ranker

A desktop app that ranks songs by having you listen and choose. Pick an artist, an album or one of
your playlists, and Song Ranker plays the songs on your own Spotify account while you decide which
ones you like more. The result is a ranked list you can save, edit and share as an image.

It runs on your computer. Your rankings, ratings and notes are stored in a local database and are
never uploaded anywhere.

> Song Ranker is an independent project. It is not made by, affiliated with, or endorsed by Spotify.

## What you can do

- Rank an artist's whole discography, a single album, or a playlist you own.
- Choose how to rank:
  - Bracket is single elimination. A list of N songs takes N - 1 comparisons and gives a winner
    plus a finishing order.
  - Pairwise runs a few rounds where each song faces others with a similar record. It takes more
    comparisons and gives a full ranked list.
  - Rate 0-10 skips comparisons. You score each song yourself (decimals are fine), then drag songs
    to adjust the order.
- Add a 0-10 rating and a note to any song while you compare. Ratings are kept across rankings.
- Take back your last pick, or reorder a finished ranking by dragging.
- Resume a ranking from History, or search and filter your past ones.
- See stats: favorite songs, favorite albums (averaged from your ratings), and totals.
- Bookmark albums to rank later. The home screen lists recent releases from artists you follow or
  listen to a lot.
- Export a ranking as a poster-style PNG to save or paste into a message.
- Dark and light themes.

## What you need

- Windows. The prebuilt jar contains the Windows JavaFX libraries. On macOS or Linux, build from
  source (see below).
- Java 21 or newer. Check with `java -version`.
- A Spotify Premium account. Spotify only allows playback control from Premium accounts, and a
  development-mode app needs a Premium owner.
- Your own Spotify developer app. There is no shared login. Spotify caps development-mode apps at
  five users, and each one has to be added by the app's owner, so everyone sets up their own.

## Set up your Spotify app (once)

1. Open <https://developer.spotify.com/dashboard>, sign in, and choose **Create app**.
2. Give it any name and description. Under **Redirect URIs** add exactly:

   ```
   http://127.0.0.1:8888/callback
   ```

   Use the IP address, not `localhost`. Spotify matches this string exactly.
3. Under **Which API/SDKs are you planning to use?** select **Web API**, then save.
4. Open the app's **Settings** and copy the **Client ID**.
5. Save the Client ID in an environment variable named `SPOTIFY_CLIENT_ID`. In PowerShell:

   ```powershell
   setx SPOTIFY_CLIENT_ID "paste-your-client-id-here"
   ```

   Open a new terminal afterwards. If you start the jar by double-clicking and it does not see the
   variable, sign out of Windows and back in. For a single PowerShell session you can use
   `$env:SPOTIFY_CLIENT_ID = "..."` instead. On macOS or Linux, use `export SPOTIFY_CLIENT_ID=...`
   in your shell profile.
6. To let someone else use your app, add them under **Settings, then User management** in the
   dashboard.

There is no client secret to configure. The login uses the PKCE flow.

## Run it

To run the jar, download `song-ranker.jar` from the Releases page (or build it, below), then either
double-click it or run:

```
java -jar song-ranker.jar
```

To run from source instead, with JDK 21+ and Maven 3.9+:

```
mvn javafx:run
```

The first time, click the link icon in the left rail. Your browser opens Spotify's approval page, and
the app continues once you accept. Port 8888 has to be free while you log in.

## Build the jar

```
mvn clean package
```

This writes `target/song-ranker.jar`, which has JavaFX, SQLite and Jackson bundled in. It contains the
JavaFX libraries for the operating system you built on. To run the tests only: `mvn test`.

## Keyboard shortcuts

| Where | Keys | Action |
|---|---|---|
| Any screen with a Back button | Esc | Go back (on a ranking screen: save and exit) |
| Head-to-head screen | Left / Right | Pick the left or right song as the winner |
| Head-to-head screen | 1 / 2 | Play the left or right song |
| Head-to-head screen | Ctrl+Z | Undo the last pick |
| History | Enter | Open the selected ranking |
| History | Delete | Delete the selected ranking (asks first) |
| History | Ctrl+F | Focus the search box |

Plain keys are ignored while a text box has focus, so typing a rating or note is unaffected.

## Where your data is

Everything is in a `.song-ranker` folder in your user directory (`%USERPROFILE%\.song-ranker` on
Windows):

| File | Contents |
|---|---|
| `song-ranker.db` | Rankings, ratings, notes, bookmarks, listened songs (SQLite) |
| `spotify-tokens.json` | Your Spotify login tokens, stored as plain JSON |
| `theme.txt` | Dark or light theme |
| `new-releases-cache.json`, `new-releases-checked.json` | Cache for the home screen's new releases |

Delete the folder to reset the app. Disconnecting in the app removes the saved tokens, and you can
also revoke access from your Spotify account settings.

## Limits and known issues

- Development-mode apps get a small request allowance from Spotify, shared across your developer
  account. If you hit it, the app says so and shows how long Spotify asked you to wait. History,
  ratings and stats keep working because they are stored locally. New releases are refreshed at most
  once a day to stay under the limit.
- Spotify only lets an app read the songs of playlists you own or collaborate on.
- Seeding a bracket by popularity is not possible. Spotify no longer lets apps like this one read
  track popularity.
- Album and track lookups are made for the US market.
- Spotify has to be open on one of your devices for playback to start. If it does not, open Spotify
  and play anything once so the device is active.

## Troubleshooting

| Message | Likely cause |
|---|---|
| `invalid_client` | `SPOTIFY_CLIENT_ID` is missing or wrong. Set it and restart the app. |
| `INVALID_CLIENT: Invalid redirect URI` | The redirect URI in the dashboard is not exactly `http://127.0.0.1:8888/callback`. |
| `403` from Spotify | The account is not in the app's User management list. |
| `Insufficient client scope` | The login predates a new permission. The app asks you to log in again on its own. |
| `Couldn't start playback` | No active Spotify device, or the account is not Premium. |

## Project layout

```
src/main/java/com/songranker/
  domain/       ranking engines and core types (no UI, no network)
  persistence/  SQLite repositories and the saved-run format
  spotify/      login, API client, new releases cache
  ui/           JavaFX screens, sessions and helpers
```

The engines are covered by unit tests, and so are the persistence layer and the resume/undo logic.

## License

MIT, see [LICENSE](LICENSE). Bundled libraries and icons are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
