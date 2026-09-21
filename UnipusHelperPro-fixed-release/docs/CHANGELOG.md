# Changelog

## Fixed build (based on 1.0.3)

### 1. Submit pacing — fixes "提交速度太快" / `600001` / `600002`

**Symptom.** After a few tasks the server rejects further submits. The
log shows:

```
INFO  Learn : Submitting too frequently, wait 2 minutes.
```

and the UI displays `提交速度太快`.

**Measured evidence** (real account, unpatched 1.0.3):

```
11:09:59,518  Submitting answers.          <- first submit of the run
11:10:41,086  Submitting too frequently, wait 2 minutes.
```

52 submits were issued in that window, i.e. roughly one every 3 seconds.
The server tolerated only a handful.

**Root cause.** The 1.0.3 client had no real pacing:

* `MAX_SUBMIT_PER_MINUTE` and `submitTimestamps` lived on `Learn`
  instances, and every `Task` creates a new `Learn`, so the counter
  always restarted at zero;
* there was no minimum interval between two submits at all —
  `Learn.learnTask()` submitted as soon as an answer was fetched.

**Fix.** New class `org.unipus.unipus.SubmitThrottle` holds the submit
history in process-wide static state, shared by every `Learn` instance:

* before each submit it computes how long to wait for
  `submit.minIntervalMs` (+ random jitter) and for the rolling
  `submit.maxPerMinute` window, and waits through
  `Task.waitForCooldown()` so pause/stop still work;
* when the server returns `600001` / `600002`, the cooldown starts at
  `submit.backoffBaseMs` and doubles on every repeated offence up to
  `submit.backoffMaxMs`, then resets after `submit.backoffResetMs`
  without a rate limit;
* all values are configurable in `unipushelper.properties`.

**Defaults:** 20 s minimum interval, 0–8 s jitter, max 3 per minute,
2 min initial backoff. Verified by test: intervals of roughly 22–28 s
were enforced, backoff went 120 s → 240 s → 480 s.

### 2. Duplicate launch — fixes the log file exception

**Symptom.**

```
main ERROR Unable to delete file ...\logs\latest.log:
java.nio.file.FileSystemException ... another program is using this file
```

**Root cause.** Two independent problems:

* `log4j2.xml` used `<OnStartupTriggeringPolicy/>`, i.e. "roll (delete)
  the existing log file on every start". On Windows the delete fails as
  soon as another JVM still has that file open.
* the program had no single-instance protection, so double-clicking the
  launcher started a second instance that fought over the same file
  (and would have operated the same account).

**Fix.**

* `Main` acquires an exclusive `FileChannel.tryLock()` on
  `logs/.unipushelper.lock`; a second instance shows a message and exits
  with code 3 instead of colliding. A watchdog thread closes that dialog
  after 20 s so no zombie process is left behind.
* every run gets its own log file: `-Duhp.logfile` is set by the
  launcher (and by `Main` as a fallback) and consumed by
  `${sys:uhp.logfile}` in `log4j2.xml`; `OnStartupTriggeringPolicy` was
  removed.

### 3. Launcher (`run.bat`) fixes

* duplicate-launch detection before starting Java;
* per-launch log file name (`logs/latest-<timestamp>.log`);
* no longer discards Java's output with `>nul 2>&1`, so failures are
  visible;
* Java discovery moved to `find-java.ps1` (checks `JAVA_HOME`, the
  registry entries under `HKLM\SOFTWARE\JavaSoft`, then `PATH`, and
  probes the real version of each candidate). The original in-batch
  `java -version` parsing produced no version on a stock Windows
  install, which degraded into an endless "choose a Java" prompt;
* paths are converted to 8.3 short form, because a `(...)` inside a
  directory name makes `cmd` abort an `if` statement with
  "… was unexpected at this time".

### 4. Packaging for this repository

* `run.bat` is ASCII-only with English messages; `run.zh-CN.bat` keeps
  the Chinese messages and is encoded in GBK (code page 936) with CRLF
  line endings so `cmd` parses it correctly;
* `unipushelper.properties.template` documents every pacing option;
* `UnipusHelperPro-fixed-1.0.3.zip` (~8.7 MB) is the ready-to-run package:
  patched jar + the 16 third-party `lib/` jars + launchers + docs +
  patch sources. Verified by extracting it to a clean folder and
  launching it through `run.bat`;
* `UnipusHelperPro-fixed-1.0.3-src.zip` (~50 KB) carries only the patch
  sources, build files and launcher sources for review/rebuild;
* `lib/README.txt` lists every bundled dependency with its upstream
  project and license;
* all credentials, logs and machine-specific paths were removed — see
  the Privacy section of the README. The packaged zip contains no `logs/`
  directory and no configuration file, only the empty template.
