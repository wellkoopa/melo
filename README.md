# Melo

Lecteur de musique YouTube Music pour **Android** et **iOS**, en Kotlin Multiplatform et Compose Multiplatform.

- Recherche de titres et de vidéos, suggestions
- Lecture en arrière-plan, contrôles sur l'écran de verrouillage
- Paroles synchronisées ([LRCLIB](https://lrclib.net))
- Favoris, playlists, historique, téléchargements hors ligne

L'accès à YouTube passe par [NewPipeExtractor-KMP](https://github.com/yushosei/NewPipeExtractor-KMP).

## Compiler

- Android : `./gradlew :androidApp:assembleDebug`
- iOS : le workflow GitHub Actions `Build iOS app` produit un `Melo.ipa` non signé (onglet *Actions* → artefact `Melo-ipa`), à installer avec SideStore ou AltStore.

## Licence

Ce projet utilise NewPipeExtractor-KMP, distribué sous licence GPL-3.0 ; Melo est donc distribué sous les mêmes conditions.
