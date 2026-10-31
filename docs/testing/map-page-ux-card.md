# Map page wording and hospital icon: phone card

Status: active procedure. Confirms the simplified SOS map and Offline Maps pages on a real phone. Tests and builds do not render the map, so the hospital icon and maps-app handoff are unproven until this card runs.

## Build identity

Branch `fix/map-page-ux`, on top of `b24463f`. Record the commit, version and APK hash used.
## Devices and setup

Use two phones with the app set up and connected. Phone A has the Cebu map installed. Phone B starts with the map deleted in Profile. The user installs, launches and operates the phones. Test once in each appearance, Night and Daylight.

## Steps and expected results

1. **Pink box:** on phone A, open a location near a hospital in central Cebu and zoom in. Expect a small white tile with a red cross at each hospital or clinic, smaller than the SOS pin, and no solid pink or red squares.
2. **Real SOS:** raise an SOS with location from phone B and open its map on phone A. Expect "SOS location", "From" and the sender's name, an SOS pin, "About N away", a direction such as "Northeast of you" and a sent time.
3. **Map buttons:** tap Show on map, the round location button and the compass. Expect the SOS pin to stay visible above the bottom sheet, and you plus the SOS both framed. Collapse and expand the sheet; the round button stays just above it.
4. **Copy and open:** tap Copy location and paste into any text field. Expect decimal coordinates. Tap Open in maps app. Expect a maps app at that point. If the phone has no maps app, expect "No maps app found. Copy the location instead."
5. **Own location unavailable:** deny the app location or turn location off, then reopen the map. Expect "Can't find your location", not an endless search.
6. **Shared location:** share a location in chat and open it. Expect "Shared location", a PIN marker, no sent time and no SOS wording. Open an incident location and expect "Incident location".
7. **No map:** repeat step 2 on phone B. Expect a calm card, not a pink box, with Download map, the sender's location, and working Copy and Open buttons. Download map opens Offline Maps; Back returns to the SOS.
8. **Offline Maps wording:** go through download, Technical details (closed at first), Delete map and its dialog. Expect plain wording with no terms such as vector, package, PMTiles or mapless.
9. **Large text:** set the largest system font size and repeat steps 2 and 7. Expect no clipped text, and all buttons reachable.

## Failure indicators

Report FAIL for any pink or red square on the map, a crash on opening the map, an SOS pin hidden behind the bottom sheet, a shared location labelled SOS, an endless "Finding your location…", Copy or Open doing nothing, or leftover technical wording.

## Markers

No new markers. On a crash, search `AndroidRuntime` in the reported window. A MapLibre style or image error can be found by searching `MapLibre`.

## Report

Build identity, phone models, appearance, per-step pass or fail, a screenshot of step 1, and any bounded failure excerpt.
