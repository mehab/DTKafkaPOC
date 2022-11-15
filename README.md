# DT-Kafka-POC

## What is this? 🤔

This project is a proof-of-concept for decoupling responsibilities from [Dependency-Track]'s monolithic API server
into separate, *scalable* services. We're using [Kafka] (or Kafka-compatible brokers like [Redpanda]) for communicating 
between API server and the PoC applications.

As of now, the PoC is capable of:

* Performing vulnerability analysis using OSS Index and Snyk
* Sending notifications via all channels supported by the original API server (E-Mail, Webhook, etc.)

We're planning to expand its set of capabilities further by:

* Performing component meta analysis (fetching latest versions from remote repositories)
* ...

An overview of the architecture can be found [here](https://excalidraw.com/#room=fba0103fa2642574be40,NomXwyHw3jvoy0yr6JxCJw).

## Great, can I try it? 🙌

Yes! We prepared demo setup that you can use to play around with the PoC.  
Check out 👉 [`DEMO.md`](DEMO.md) 👈 for details!

## Technical Documentation 💻

### Configuration 📝

See [`CONFIGURATION.md`](CONFIGURATION.md).

### Verify if Cwe store contains values

Get request to http://localhost:8089/cwe/data?id=178
<br/>
<br/>
### Creating and using an externally created SecretKey
It is now possible to generate a secret key externally using jar file created from [keyGeneration](https://github.com/mehab/KeyGeneration).
This will create the secret key in the folder `~/.dependency-track` and will be used by the DT API server as well as this poc code. <br/>
The usage with the poc code can happen once we have hibernate implementation complete then we can fetch secrets directly from DT database and decrypt it using the SecretsUtil added to this project. <br/>
Create the jar of the keygeneration code using `./mvnw package` and then use the runnable jar created in the target folder of the keyGeneration app to generate the key. <br/>
For this key to be effective, this step needs to be performed before starting off dependency track api server and quarkus application. And it is a manual step as of now. <br/>

### Testing 🤞

### Vulnerability Analysis 💀

The `test.sh` script can be used to test the vulnerability analyzer.  
It publishes an event to the `component-analysis` topic for each component with a
PURL in a given CycloneDX BOM, thus emulating the API server after a BOM was uploaded to it.

```shell
./test.sh bom.json
```

> **Note**
> The script requires `jq` and `rpk` to be installed.  
> They can be installed using Homebrew:
> ```shell
> brew install jq
> brew install redpanda-data/tap/redpanda
> ```

Analysis results that are intended for consumption by the API server will be
published to the `component-vuln-analysis-result` topic.

## Redpanda Console

The provided `docker-compose.yml` includes an instance of [Redpanda Console](https://github.com/redpanda-data/console)
to aid with gaining insight into what's happening in the message broker. Among many other things, it can be used to
inspect messages inside any given topic:

![Redpanda Console - Messages](.github/images/redpanda-console_messages.png)

The console is exposed at `http://127.0.0.1:28080` and does not require authentication. It's intended for local use only.

## Running this poc with Dependency Track
Follow the below steps to run this poc in integration with dependency track for an end to end test. Currently, we have been able to implement sending of events from DT to a topic which is being polled by this poc application.
- If you want to share the secret key for data decryption between dependency track api server and this poc code, you need to generate the secret key using the steps mentioned in the section `Creating and using an externally created SecretKey`. This step needs to be performed prior to starting off DT api server and poc in that case.
- Use this branch of dependency track backend server from git: `https://github.com/sahibamittal/dependency-track/tree/ingest-vuln-analysis-results`
- Run the dependency track api server using provided maven <b>jetty profile</b>.
- Use this branch of dependency track front end from git: `https://github.com/sahibamittal/dependency-track-frontend/tree/internal-dt-latest`
- Run the frontend using `npm ci` and then `npm run serve`
- Run the containers for the poc code using `docker compose up`
- Run the poc code using: `quarkus dev -Dalpine.application.properties=./alpine.properties`
- Run redpanda console on `http://localhost:28080`
- Login to dependency track application running locally on the port pointed by the front end code
- Create a project and load an sbom into dependency track
- You should be able to see events appearing in the event new topic in the redpanda console. You should also be able to see logs in the quarkus application as it analyzes the components on the posted bom components in the topic. The latter would be based on the configuration you used to start the poc application.

[Dependency-Track]: https://github.com/DependencyTrack/dependency-track
[Kafka]: https://kafka.apache.org/
[Redpanda]: https://redpanda.com/