# First-run onboarding

Three swipeable pages introduce PembacaKUE, teach the NFC tap, and explain voice balance / scan history. Next, Back, and Skip are available. The final page reflects the device's real NFC state, with a system-settings action for disabled NFC and an explanation of available features for devices without NFC.

Completion is stored separately in `pembacakue_onboarding.xml`, excluded from cloud backup and device transfer. It survives app updates but a new installation receives the tour. Settings → Lihat panduan awal reopens the guide. NFC reader mode, ad consent, and update prompts wait until the first tour is complete. Launch NFC intents and shortcuts are deferred until completion.

Illustrations were generated with the built-in imagegen tool, with transparent backgrounds preserved. Production assets live in `app/src/main/res/drawable-nodpi/`. The exact prompts are recorded in `prompts.md`.

Manual device QA checklist: fresh install → guide; next/swipe/back → correct page; skip/finish → app; relaunch → no guide; rotation/recreation → current page; NFC disabled → settings → refreshed status; no NFC → other features remain accessible; settings → reopen guide; large font / small display → scrollable content and reachable navigation.
