# Pipeliner

Full-stack collaboration platform to host HTML/JS applications and resources developed by a distributed team with:
* JVM backend to hide the internal filesystem behind a safe API
* WasmJS frontend to expose a file browser to easily read and write files

If any folder contains an `index.html`, it is considered as an app, and could be launched by pressing the green triangle (or some other button ¦3).

I use it at [mattemade.net](https://mattemade.net) to share the in-progress HTML5 game prototypes with people who help me with them, and to
allow everyone to update the resources independently. Probably does not work with Safari though as I don't have anything with
macOS to make it work, and Apple does not have enough resources to support basic web specification on their platforms.

### Notes for myself

#### How to update the backend

* stop the java process on the server
* build a new JAR with `gradlew :server:buildFatJar`
* upload it with `scp build/libs/server-all.jar root@mattemade.net:/home/full/server-all.jar`
* start the server with `cd /home/full && java -jar server-all.jar &`

#### How to update the frontend

* build a new version with `gradlew :composeApp:wasmJsBrowserDistribution` (maybe try if `jsBrowserDistribution` works, not sure ¦3)
* drag-and-drop everything from `composeApp/build/dist/wasmJs/productionExecutables/` to [this folder](https://mattemade.net/?wasmJs/productionExecutable)

#### How to update SSL cert

Find the doc from Let's Encrypt and follow it.

After SSL certificate is updated do this:
```bash
openssl pkcs12 -export -in /etc/letsencrypt/live/mattemade.net/cert.pem -inkey /etc/letsencrypt/live/mattemade.net/privkey.pem -out pipelinerKeyStore.p12 -name "pipelinerAlias"
keytool -importkeystore -srckeystore pipelinerKeyStore.p12 -srcstoretype pkcs12 -destkeystore pipelinerKeyStore.jks
```
Use `notASecret` as a password for everything that requires a password.

Restart the server right after.