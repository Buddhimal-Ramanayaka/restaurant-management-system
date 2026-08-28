# Local Setup Guide — Windows

Getting the Restaurant Management System running on Windows 10 or 11, start to finish.
Budget 45–60 minutes the first time, most of which is installers and dependency downloads.

All commands below are **PowerShell**. Open it with `Win + X` → *Terminal* (Windows 11) or
*Windows PowerShell* (Windows 10). A few steps need **Administrator** — those are marked.

> There is a companion `SETUP.md` in this folder written for macOS/Linux. Use this file
> instead; several steps differ in ways that matter, not just in path separators.

---

## Step 0 — Prerequisites

| Tool | Version | Verify with |
|---|---|---|
| JDK | 21 (LTS) | `java -version` |
| Gradle | 8.5+ | `gradle -v` |
| MySQL | 8.0 | `mysql --version` |
| Node.js | 18+ | `node -v` |
| Docker Desktop | any recent | `docker ps` — **only for integration tests** |

Java 21 specifically. The code uses Java 21 language features and will not compile on 17.

### Installing with winget (fastest route)

`winget` ships with Windows 11 and recent Windows 10. In an **Administrator** PowerShell:

```powershell
winget install Microsoft.OpenJDK.21
winget install Gradle.Gradle
winget install OpenJS.NodeJS.LTS
winget install Oracle.MySQL
```

If a package ID has changed, find the current one with e.g. `winget search openjdk`.

**Close and reopen PowerShell after installing** — PATH changes don't apply to already-open
windows. Then verify all four:

```powershell
java -version; gradle -v; node -v; mysql --version
```

### If `mysql` is not recognised

The MySQL client isn't added to PATH by the installer. Add it (Administrator PowerShell,
adjust the version folder if yours differs):

```powershell
$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin" [Environment]::SetEnvironmentVariable("Path", [Environment::GetEnvironmentVariable("Path", "Machine") + ";$mysqlBin","Machine")
```
ංං
Reopen PowerShell and try `mysql --version` again.

### Allow npm scripts to run

Windows blocks PowerShell scripts by default, and npm ships as `npm.ps1`. Without this
you'll get *"npm.ps1 cannot be loaded because running scripts is disabled on this system"*
the moment you try `npm install`. Fix it once, per user (no Administrator needed):

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

## Step 1 — Unpack the project

```powershell
Expand-Archive -Path .\RMS_Full_Source_Code.zip -DestinationPath .
cd .\rms
```

You should see `backend`, `frontend`, `README.md`, `TESTING.md`, and this file.

Avoid unpacking into a deeply nested path. Gradle and npm both generate long nested paths
of their own, and Windows' 260-character path limit is easy to hit. Somewhere shallow like
`C:\dev\rms` or `D:\rms` is safer than `C:\Users\You\Documents\University\Year 4\...`.

---

## Step 2 — Confirm MySQL is running

```powershell
Get-Service MySQL8
```

If `Status` is `Stopped`, start it (Administrator PowerShell):

```powershell
Start-Service MySQL8
```

The service may be named differently (`MySQL80`, `MySQL84`, `MySQL`) depending on your
installer version — `Get-Service *MySQL*` will show you what's actually there.

**If more than one MySQL service shows up** (e.g. both `MySQL8` and `MySQL80` — this
happens when MySQL was installed more than once, such as a standalone install plus a
bundled one from another tool like XAMPP/Workbench), only one can hold port 3306 at a
time, and it may not be the one with this project's database on it. Symptoms: the app
loads, but every login fails with "Invalid username or password" even with correct
credentials — the backend is reaching a *different*, empty MySQL instance. Fix (Administrator
PowerShell):

```powershell
Get-Service *MySQL*                 # see which ones exist and their Status
Stop-Service -Name MySQL80 -Force   # stop the wrong one
Start-Service -Name MySQL8          # start the one with rms_db on it
```

If you're unsure which instance actually has `rms_db`, stop all but one, start it, and
test with `mysql -u rms_user -prms_password rms_db -e "SELECT COUNT(*) FROM menu_items;"`
— it should return 46. An "Access denied" or empty result means you started the wrong one.

---

## Step 3 — Create the database

```powershell
mysql -u root -p
```

Enter the root password you set during MySQL installation, then run:

```sql
CREATE DATABASE rms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'rms_user'@'localhost' IDENTIFIED BY 'rms_password';
GRANT ALL PRIVILEGES ON rms_db.* TO 'rms_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### Loading schema.sql — read this carefully

**PowerShell does not support the `<` input redirect.** The Linux-style command
`mysql -u rms_user -p rms_db < schema.sql` fails with:

```
The '<' operator is reserved for future use.
```

Use one of these instead. **Option A** (pipe the file in — recommended):

```powershell
Get-Content .\backend\src\main\resources\schema.sql -Raw | mysql -u rms_user -p rms_db
```

**Option B** (hand the redirect to cmd.exe, which does support it):

```powershell
cmd /c "mysql -u rms_user -prms_password rms_db < backend\src\main\resources\schema.sql"
```

Note there is **no space** after `-p` in Option B — that's how MySQL takes an inline
password. It also means the password lands in your shell history, so prefer Option A.

**Option C** — open MySQL Workbench, connect as `rms_user`, then *File → Open SQL Script*,
select `schema.sql`, and click the lightning-bolt Execute button.

### Verify it loaded

```powershell
mysql -u rms_user -p rms_db -e "SELECT COUNT(*) AS tables_created FROM information_schema.tables WHERE table_schema='rms_db';"
mysql -u rms_user -p rms_db -e "SELECT username, role FROM users;"
```

Expect **19 tables** and five user accounts (`admin`, `manager`, `waiter`, `kitchen`,
`cashier`). If the user table is empty, the seed section didn't run — see Troubleshooting.

> `schema.sql` is built to run **once against an empty database**. Re-running it on a
> populated one fails on duplicate-key errors from the seed inserts. To start over:
> `DROP DATABASE rms_db;` then repeat this step from the top.

---

## Step 4 — Generate the Gradle wrapper

The zip has no `gradlew.bat` / `gradle-wrapper.jar` — those are binaries that couldn't be
downloaded in the environment this code was generated in. Create them once:

```powershell
cd backend
gradle wrapper --gradle-version 8.10
```

From now on use `.\gradlew.bat`, and Gradle itself no longer needs to be on your PATH.

---

## Step 5 — Configure and run the backend

The defaults in `application.yml` already match the database from Step 3, so `JWT_SECRET`
is the only variable you genuinely must set. Still in the `backend` folder:

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="rms_db"
$env:DB_USER="rms_user"
$env:DB_PASSWORD="rms_password"
$env:JWT_SECRET="replace-this-with-your-own-long-random-string-at-least-32-chars"
$env:CORS_ORIGINS="http://localhost:5173"
```

> **`$env:` variables live only in the PowerShell window you typed them in.** Close that
> window, or open a new tab, and they're gone — the backend will then fall back to the
> `application.yml` defaults and, more importantly, to the placeholder JWT secret. If you'd
> rather set them permanently, use
> `[Environment]::SetEnvironmentVariable("JWT_SECRET","your-secret","User")` and reopen
> PowerShell.

> `JWT_SECRET` **must be at least 32 characters.** JJWT derives an HMAC-SHA256 key from it
> and throws `WeakKeyException` on anything shorter, crashing the app at startup.

Build and run:

```powershell
.\gradlew.bat build -x test
java -jar build\libs\rms-backend-0.1.0.jar
```

`-x test` skips the test task, which needs Docker (Step 8).

Windows Defender Firewall will likely pop up asking whether to allow Java to accept
connections — **Allow access** on private networks, or the frontend can't reach the API.

Watch for `Started RmsApplication in X seconds`.

### Confirm login works

In a **second** PowerShell window:

```powershell
$body = @{ username = "admin"; password = "admin123" } | ConvertTo-Json Invoke-RestMethod -Uri http://localhost:8080/api/auth/login -Method Post -Body $body -ContentType "application/json"
```

You should get back an object with `token`, `username`, `role`, and `userId`.

> Don't use `curl` here. In Windows PowerShell 5.1 `curl` is an **alias for
> `Invoke-WebRequest`**, which takes completely different arguments — the Linux-style
> `curl -X POST -H ... -d ...` will fail with confusing parameter errors. Use
> `Invoke-RestMethod` as above, or call `curl.exe` explicitly to get the real binary.

If that token comes back, your backend, database, schema, and BCrypt verification are all
working correctly.

---

## Step 6 — Run the frontend

In that second PowerShell window, from the project root:

```powershell
cd frontend
npm install
Copy-Item .env.example .env.local
npm run dev
```

Open **http://localhost:5173**.

---

## Step 7 — Click through it end to end

All five seeded accounts use the password **`admin123`**.

| Username | Role | Lands on |
|---|---|---|
| `admin` | ADMIN | Manager Dashboard |
| `manager` | MANAGER | Manager Dashboard |
| `waiter` | WAITER | POS Terminal |
| `kitchen` | KITCHEN | Kitchen Display |
| `cashier` | CASHIER | POS routes |

**To watch the real-time flow, open two browser windows side by side** — one as `waiter`,
one as `kitchen`. Make the second an **InPrivate/Incognito window**, otherwise the two
sessions overwrite each other's token in localStorage and you'll get logged out of one.

1. **As `waiter`:** pick a green table → tap items to build a cart → add a note like
   "no spicy" → **Send to Kitchen**.
2. **Watch the `kitchen` window.** The ticket appears in the New lane within a second, with
   no page refresh. That's the STOMP broadcast.
3. **As `kitchen`:** tap **Start Cooking**, tick each line done, then **All Ready**.
4. **Watch the `waiter` window.** A green "Order ready" banner appears in the header —
   that's the point-to-point `/user/queue/order-ready` push.
5. **As `manager`:** order two or three **Prawn Curry**. Prawns are seeded at 2.5 kg against
   a 2.0 kg reorder level, so this trips the threshold — you'll see a live stock alert on
   the dashboard plus an auto-drafted purchase order.
6. Keep ordering Prawn Curry until stock hits zero. Prawn Curry should grey out as
   unavailable on the POS grid automatically.

---

## Step 8 — Run the tests

**Frontend** (no Docker needed — 39 tests, all currently passing):

```powershell
cd frontend
npm test
```

**Backend unit tests** (no Docker needed — 26 tests):

```powershell
cd backend
.\gradlew.bat test --tests "com.rms.service.*" --tests "com.rms.security.*"
```

**Backend integration tests** — these need **Docker Desktop running**, with the WSL2 backend
enabled (Docker Desktop → Settings → General → *Use the WSL 2 based engine*). Testcontainers
starts a throwaway MySQL container per test class.

```powershell
docker ps
.\gradlew.bat test --tests "com.rms.integration.*"
```

Everything at once: `.\gradlew.bat test`

> Honest caveat: the frontend suite was actually executed and verified passing. The backend
> suite was written and reviewed carefully but **never executed** — the environment it was
> written in had neither Docker nor access to Maven Central. Expect to fix a compile error
> or two on the first run. `TESTING.md` has detail.

---

## Troubleshooting

**`The '<' operator is reserved for future use.`**
You used Linux-style input redirection in PowerShell. See Step 3 — use `Get-Content ... |
mysql ...` or wrap the command in `cmd /c "..."`.

**`npm.ps1 cannot be loaded because running scripts is disabled on this system`**
Run `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`, then reopen
PowerShell.

**`java` / `gradle` / `mysql` `is not recognized as the name of a cmdlet`**
Either not installed, or PATH hasn't refreshed. Close every PowerShell window and open a
new one — PATH changes don't reach already-open sessions.

**`gradlew.bat is not recognized`**
You skipped Step 4, or you're in the wrong directory. The wrapper lives in `backend\`. In
PowerShell you must prefix it: `.\gradlew.bat`, not bare `gradlew.bat`.

**`Schema-validation: missing table [xyz]` at startup**
Hibernate's `ddl-auto: validate` found the live schema doesn't match the JPA entities.
Nearly always means Step 3 didn't fully complete. Reset:

```powershell
mysql -u root -p -e "DROP DATABASE rms_db; CREATE DATABASE rms_db CHARACTER SET utf8mb4;"
mysql -u root -p -e "GRANT ALL PRIVILEGES ON rms_db.* TO 'rms_user'@'localhost'; FLUSH PRIVILEGES;"
Get-Content .\backend\src\main\resources\schema.sql -Raw | mysql -u rms_user -p rms_db
```

**`Access denied for user 'rms_user'@'localhost'`**
The user wasn't created, or `$env:DB_PASSWORD` doesn't match Step 3. Re-run the
`CREATE USER` / `GRANT` statements.

**`Public Key Retrieval is not allowed`**
MySQL 8's `caching_sha2_password` plugin. The JDBC URL in `application.yml` already sets
`allowPublicKeyRetrieval=true`, so this means you overrode the URL somewhere — add the
parameter back.

**`WeakKeyException` at startup**
`JWT_SECRET` is under 32 characters. Lengthen it. Also check you're in the same PowerShell
window where you set it.

**Port 8080 already in use**
Find and kill the process:

```powershell
netstat -ano | Select-String ":8080"
taskkill /PID <the-PID-from-above> /F
```

Or run on a different port:
`java -jar build\libs\rms-backend-0.1.0.jar --server.port=8081` — then update
`VITE_API_BASE_URL` and `VITE_WS_URL` in `frontend\.env.local` to match.

**Login returns 401 with the seeded accounts**
Check the seed rows loaded: `mysql -u rms_user -p rms_db -e "SELECT username FROM users;"`.
Empty table means the seed section of `schema.sql` didn't execute — reload it.

**Frontend loads but every API call fails; browser console shows a CORS error**
`CORS_ORIGINS` must match the frontend origin exactly, scheme included —
`http://localhost:5173`, not `localhost:5173`. Restart the backend after changing it.

**Frontend can't reach the backend at all (connection refused)**
Windows Defender Firewall may have silently blocked Java. Check *Windows Security → Firewall
& network protection → Allow an app through firewall* and confirm Java/OpenJDK is permitted
on Private networks.

**Kitchen Display shows "Reconnecting…" and never turns green**
Open DevTools (`F12`) → Network → look for the `/ws` request. Most common cause is a stale
JWT — log out and back in. Also confirm the backend is running; the STOMP client retries
every 4 seconds forever without surfacing a visible error.

**Integration tests: `Could not find a valid Docker environment`**
Docker Desktop isn't running, or the WSL2 backend is off. Start Docker Desktop, wait for the
whale icon to go steady, confirm with `docker ps`, then retry.

**Build fails with path-length errors**
Move the project somewhere shallow (`C:\dev\rms`). Or enable long paths, in an Administrator
PowerShell:

```powershell
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
  -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
```

---

## Before this goes anywhere real

- Replace every seeded password. All five demo accounts share `admin123`.
- Set a genuinely random `JWT_SECRET` — never the `application.yml` placeholder.
- Put the app behind HTTPS. JWTs in `Authorization` headers over plain HTTP are readable by
  anyone on the network.
- Turn on MySQL automated backups. `inventory_ledger` is append-only and is the only
  forensic record of stock movement — losing it loses your entire audit trail.
