# Deploying to an Oracle Cloud "Always Free" VM

Stack on the VM (Docker Compose): **Caddy** (HTTPS, the only public entry) -> **app** (Spring Boot + the PWA) -> **PostgreSQL** (internal network only, no published port).

> Oracle's free-tier terms and console wording change. If a step differs on screen, follow the intent.
> Never put `.env` or backups in git.

## 1. Create the VM (Oracle console, ~15 min)

1. Sign up at oracle.com/cloud/free (a card is needed for identity checks; Always Free resources are not billed). Pick a **home region** close to Indonesia (e.g. Singapore) - it cannot be changed later.
2. **Compute > Instances > Create instance**
   - Image: **Ubuntu 22.04 or 24.04** (aarch64 for Ampere)
   - Shape: **Ampere `VM.Standard.A1.Flex`**, 2 OCPU / 12 GB RAM (the free allowance is up to 4 OCPU / 24 GB). If "out of capacity", retry later or try another availability domain. The AMD `VM.Standard.E2.1.Micro` (1 GB) is too small for Spring Boot + Postgres.
   - Networking: public subnet, **assign a public IPv4**
   - Add your **SSH public key** (or let Oracle generate one and download it; keep it safe).
3. Note the public IP. Optional but recommended: **Networking > Reserved public IPs**, create one and attach it to the instance so it never changes.
4. **Open the firewall in the cloud:** Networking > Virtual Cloud Networks > your VCN > Security Lists > Default > **Add ingress rules**
   - Source `0.0.0.0/0`, TCP, destination port **80**
   - Source `0.0.0.0/0`, TCP, destination port **443**
   - Leave port 22 as is (or restrict the source to your own IP).

## 2. Prepare the server

```bash
ssh -i <your-key> ubuntu@<PUBLIC_IP>

# Oracle's Ubuntu image also blocks ports with iptables - open 80/443 on the machine itself:
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80  -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo apt-get update && sudo apt-get install -y netfilter-persistent iptables-persistent
sudo netfilter-persistent save

# Docker + automatic security updates
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
sudo apt-get install -y unattended-upgrades
exit   # log in again so the docker group applies
```

## 3. Get a free hostname (no domain purchase)

1. Go to **duckdns.org**, sign in, create a subdomain, e.g. `cvlievera` -> `cvlievera.duckdns.org`.
2. Set its IP to the VM's public IP.
3. HTTPS is mandatory (cookies are `Secure`, HSTS is on, and the PWA needs it to install). Caddy gets a free Let's Encrypt certificate for this name automatically once ports 80/443 are open.

(If you own a domain, add an `A` record to the VM IP instead.)

## 4. Put the code on the server

From your **Windows PC** (PowerShell), without needing GitHub:

```powershell
cd D:\myProjects
tar --exclude=target --exclude=.git --exclude=application-local.properties -czf cvl.tgz cv-lievera-stock-management
scp -i <your-key> cvl.tgz ubuntu@<PUBLIC_IP>:~
```

On the VM:

```bash
tar xzf cvl.tgz && cd cv-lievera-stock-management
chmod +x scripts/*.sh mvnw
```

## 5. Configure secrets

```bash
cp .env.example .env
chmod 600 .env
nano .env        # set DOMAIN and fill every value; generate secrets with:
                 #   openssl rand -base64 24   (DB_PASSWORD, BACKUP_PASSPHRASE)
                 #   openssl rand -base64 48   (JWT_SECRET)
                 #   openssl rand -base64 32   (APP_ENCRYPTION_KEY)
```

Set `BOOTSTRAP_ADMIN_PASSWORD` to a strong one-time password (12+ chars, 3 of 4 character types). **Save `.env` (especially `APP_ENCRYPTION_KEY` and `BACKUP_PASSPHRASE`) in a password manager** - losing the encryption key makes every enrolled 2FA unusable, and losing the passphrase makes backups unreadable.

## 6. Start it

```bash
docker compose up -d --build     # first build takes a few minutes
docker compose logs -f app       # wait for "Started CvLieveraStockManagementApplication", Ctrl+C to leave
```

Open `https://<your-name>.duckdns.org`:
1. Sign in as `admin` with `BOOTSTRAP_ADMIN_PASSWORD`.
2. Choose a new password, then enrol your authenticator app and **save the recovery codes**.
3. Remove `BOOTSTRAP_ADMIN_PASSWORD` from `.env`, then `docker compose up -d`.
4. Create staff accounts under **Users**. On a phone: open the site, then "Add to Home Screen".

## 7. Backups (do this before real data goes in)

```bash
./scripts/backup.sh                       # test it once
crontab -e
# add (nightly at 02:30):
30 2 * * * cd /home/ubuntu/cv-lievera-stock-management && ./scripts/backup.sh >> backups/backup.log 2>&1
```

Backups are AES-256 encrypted, kept 14 days in `backups/`. The disk of the same VM is **not** a safe place on its own: also copy the files off the server regularly (for example `scp`/`rclone` to Google Drive or to your PC; they are encrypted, so this is safe).

**Test a restore** at least once on a scratch copy: `docker compose stop app && ./scripts/restore.sh backups/<file> && docker compose start app`.

## 8. Updating the app

Repeat step 4 (new tarball, keep your existing `.env` and `backups/`), then:

```bash
docker compose up -d --build
```

Database migrations (Flyway) run automatically on start.

## 9. Operating notes

- **Idle reclamation:** Oracle may reclaim Always Free instances that stay almost idle for a long time. Real use plus the nightly backup normally prevents this; check the console occasionally.
- **Logs:** `docker compose logs --tail 200 app`. Failed logins, lockouts and other security events are in the in-app **Audit log** (admins).
- **Restart after reboot:** containers use `restart: unless-stopped`, so they come back by themselves.
- **SSH:** key-only login (Oracle default). Don't enable password auth.
- **Audit integrity:** the Audit page shows whether the hash chain verifies; investigate immediately if it does not.
