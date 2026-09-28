# App-project

## App name

    JDRNexus

## Pitch

    Playing tabletop roleplaying games requires managing multiple tools, from character sheets and lore to dice and voice chats. Our application centralizes these elements into a single, interactive hub featuring a  dedicated shared "workspace" for all participants of a campaign (a player can be in multiple groups). Unlike a simple drive, the app will offer a mobile-first, highly readable interface. Players can instantly update their stats (such as taking damage) using intuitive buttons. These actions trigger real-time synchronization across all devices, eliminating the friction of manual typing and preserving the game's immersion.
     It will also provide in-session tools, such as a dice roller and a voice chat to enable people to play even if they cannot be in the same place
    It is designed for both players and game masters.


## Split-app model

    Documents must be shared among different users and thus stored online with real-time synchronization. We will do this using Cloud Firestore.
    The app will also use Google Sign-in through Firebase Authentication as an authentication service and use Firebase Cloud Messaging will be utilized to push notifications for campaign updates and upcoming session reminders
## Multi-user support

    Users will log into the app using an authentication protocol and a username, allowing players to invite each other. Inside the app, each user will have a personal space. Within a group, the game master will act as an "admin" who can modify any shared documents.
## Sensor use

    The app utilizes the GPS and a map feature so a game master can propose a game at a specific location, allowing other users to see all sessions available around their position.
    Furthermore, the app integrates the device's microphone and speaker to facilitate remote play, providing a built-in voice channel for seamless communication among group members.
## Offline mode

    In offline mode, users will still be able to manage their personal data (create characters, write personal lore, etc.) and use the dice rolling feature. Group features synchronisation will be unavailable while offline.
    However, Cloud Firestore features native 'Offline Persistence.' This means any campaign documents accessed before losing connection are automatically cached locally. As a result, both players and the game master can continue consulting essential files, ensuring sessions can still be held in locations with poor network connectivity.
