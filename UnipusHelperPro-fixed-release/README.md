# UnipusHelperPro (fixed build)

A patched build of **UnipusHelperPro 1.0.3** — a desktop GUI helper for
U校园 (Unipus) AI-version courses.

This build fixes two practical problems of the 1.0.3 release:

1. **"Submitting too fast" (`提交速度太快`)** — the client had no real
   pacing between submissions, so the server started rejecting submits
   with code `600001` / `600002` after a handful of tasks.
2. **Duplicate-launch crash** — starting the program twice made two JVMs
   fight over the same `logs/latest.log`:
   `Unable to delete file ... another program is using this file`.

The original program is not included as source here; only the patched
classes and the launcher are provided. See **Credits** at the bottom.

---

## Download (easiest way)

Grab the ready-to-run archive from this repository:

| File | Size | What it is |
|---|---|---|
| **`UnipusHelperPro-fixed-1.0.3.zip`** | ~8.7 MB | **The program.** Jar + `lib/` dependencies + launchers + docs. Unzip anywhere and double-click `run.bat`. |
| `UnipusHelperPro-fixed-1.0.3-src.zip` | ~50 KB | Patch sources and build files only, for people who want to review or rebuild. No runnable jar, no dependencies. |

Click the zip in the file list, then press the **Download raw file**
button (or use the URL below):

```
https://github.com/<your-name>/<your-repo>/raw/main/UnipusHelperPro-fixed-1.0.3.zip
```

After unzipping you get a folder `UnipusHelperPro-fixed-1.0.3/` that is
complete — nothing else to download.

---

## Requirements

| Item | Requirement |
|---|---|
| OS | Windows 10 / 11 |
| Java | **JDK / JRE 25 or newer** |
| Browser | not needed — the program talks to the Unipus HTTP API directly |

Java 25 is a hard requirement: the release classes are compiled with
class file version 69 (Java 25).

---

## Quick start

1. Download `UnipusHelperPro-fixed-1.0.3.zip` and unzip it.
2. Keep the folder as it is: the jar's manifest references `lib/*.jar`
   with a relative path, so `lib/` must stay next to the jar.
3. Optionally copy `unipushelper.properties.template` to
   `unipushelper.properties` and tune the pacing (see below).
4. Double-click **`run.bat`** (English) or **`run.zh-CN.bat`** (Chinese).
5. In the window that opens, enter **your own** U校园 account and
   password. Nothing is pre-filled and nothing is bundled.

The launcher will:

* refuse to start a second instance while one is running;
* give this run its own log file `logs/latest-<timestamp>.log`;
* locate a Java 25+ runtime automatically (`JAVA_HOME`, the registry,
  then `PATH`), via `find-java.ps1`.

---

## Submit pacing (the important part)

The program submits answers to the Unipus server. Submitting too fast
makes the server answer with `600001` / `600002`, which the UI shows as
**"提交速度太快"**. Measured on a real account: 12 submissions in 41
seconds was enough to trigger it.

This build enforces a global minimum interval between submissions and
backs off exponentially when the server still complains. Defaults:

| Setting | Default | Meaning |
|---|---|---|
| `submit.minIntervalMs` | `20000` | wait at least 20 s between two submissions |
| `submit.jitterMs` | `8000` | add 0–8 s of random jitter on top |
| `submit.maxPerMinute` | `3` | at most 3 submissions per rolling minute |
| `submit.backoffBaseMs` | `120000` | first cooldown after a rate-limit reply |
| `submit.backoffMaxMs` | `900000` | cooldown ceiling (doubles each repeat) |
| `submit.backoffResetMs` | `600000` | idle time that resets the backoff |

Put these in `unipushelper.properties` next to the jar, or pass them as
JVM properties, e.g. `-Dsubmit.minIntervalMs=30000`. A commented template
is generated automatically the first time a task starts learning; delete
the file to go back to the built-in defaults.

**Recommendation:** keep `minIntervalMs` at 20000 or higher. Values below
~10000 were observed to be rejected by the server. Slower is safer: the
program is meant to run unattended for a long time, not to finish fast.

---

## Privacy

This repository contains **no** credentials, tokens, logs, machine paths
or any other personal data:

* no account, password or API key is bundled — the login dialog is empty
  and you type your own credentials;
* `logs/` is generated locally at runtime and is ignored by
  `.gitignore`; those logs contain your account id and course data, so
  **do not commit them**;
* the patched classes were checked to contain no personal strings.

The program stores an optional "remember me" username through the Java
`Preferences` API (registry `HKCU\Software\JavaSoft\Prefs\org\unipus`),
i.e. outside this repository, on your machine only.

Report/log output can contain course and answer content — redact it
before pasting into an issue.

---

## Layout

```
UnipusHelperPro-fixed-1.0.3.zip      ready-to-run program (jar + lib + launchers)
UnipusHelperPro-fixed-1.0.3-src.zip  patch sources / build files only
run.bat                        launcher (English messages, ASCII)
run.zh-CN.bat                  launcher (Chinese messages, GBK encoding)
find-java.ps1                  locates a Java 25+ runtime, prints its 8.3 path
unipushelper.properties.template   pacing configuration template
UnipusHelperPro-1.0.3.jar      patched application jar
build/
  org/unipus/Main.java                 source of the patched entry point
  org/unipus/unipus/Learn.java         source of the patched submit path
  org/unipus/unipus/SubmitThrottle.java   new: the submit pacer
  classes/                             compiled classes ready to repack
  log4j2.xml                           logging configuration (patched)
  MANIFEST.MF                          jar manifest
  run-ascii.bat, run.zh-CN.bat         launcher sources
docs/
  SETUP.zh-CN.md               setup notes (Chinese)
  CHANGELOG.md                 what changed and why
```

The repository itself does **not** carry a usable `lib/` folder — it lives
inside `UnipusHelperPro-fixed-1.0.3.zip` (16 third-party jars, ~9 MB). If
you work from the raw repository files instead of the zip, copy `lib/`
from the original 1.0.3 release; the patched jar keeps the original
manifest, so `lib/` must be present for the program to start.

### Package contents

```
UnipusHelperPro-fixed-1.0.3/
  UnipusHelperPro-1.0.3.jar          patched program
  lib/                               16 third-party jars (see lib/README.txt)
  run.bat  run.zh-CN.bat             launchers (English / Chinese)
  run.sh                             Linux/macOS helper (unchanged)
  find-java.ps1                      Java 25+ locator
  unipushelper.properties.template   pacing template
  README.md                          this file
  docs/                              CHANGELOG.md, SETUP.zh-CN.md
  src-patch/                         patch sources + compiled classes
```

---

## Rebuilding after editing the sources

The patched classes must be compiled with Java 25+ and packed back into
the jar:

```bat
set JDK=C:\Program Files\Java\jdk-26.0.1
set CP=lib\*;UnipusHelperPro-1.0.3.jar
"%JDK%\bin\javac" -encoding UTF-8 --release 25 -cp "%CP%" -d build\classes ^
    build\org\unipus\Main.java ^
    build\org\unipus\unipus\SubmitThrottle.java ^
    build\org\unipus\unipus\Learn.java
cd build\classes
"%JDK%\bin\jar" uf ..\..\UnipusHelperPro-1.0.3.jar org\unipus\Main.class ^
    org\unipus\unipus\Learn.class "org\unipus\unipus\Learn$1.class" ^
    org\unipus\unipus\SubmitThrottle.class
```

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Unable to delete file ... latest.log` | You started a second instance. This build blocks that and gives each run its own log file. |
| `Java 25 or newer was not found` | Install JDK 25+ (e.g. Temurin) or set `JAVA_HOME`. |
| Window does not appear, nothing happens | Run `run.bat --foreground` to keep a console and read the error. |
| Still `提交速度太快` occasionally | Raise `submit.minIntervalMs` (e.g. 30000) and restart. |
| Garbled Chinese in `run.zh-CN.bat` | Your console is not using code page 936; use `run.bat` instead. |

---

## Credits and disclaimer

* Original program: **UnipusHelperPro 1.0.3** by its original author.
  This repository ships a patched build of that release plus the
  patched sources; the rest of the code is unchanged.
* This build only adds pacing/robustness fixes. It does not bypass any
  authentication and requires your own valid account.
* Use at your own risk and in line with your school's and Unipus's
  terms of service.
