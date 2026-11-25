# core/ — Lab automation modules (sirf apne khud ke system/lab ke liye)

| Module | Marvel naam | Kaam |
|---|---|---|
| `router.py` | Ultron | Task dekh kar fast ya powerful model chuno |
| `security.py` | Vision | Har command/URL ko chalne se pehle check karo (default-deny) |
| `memory.py` | Miss Minutes | `data/memory.json` aur `data/logs.json` me save/padho |
| `runner.py` | Bastion & Nimrod | `run_linux_command`, `check_web_status`, `scan_local_vulnerability` |
| `server.py` | — | Android app ke liye LAN webhook (secret-protected) |

## Chalana
```
cd ai-assistant
export LAB_SECRET="kuch-lamba-random-string"
python3 -m core.server
```
Phone app ke Settings → Lab backend me isi machine ka `http://<LAN-IP>:8765` aur yahi secret daalo.

## Seema (zaroor padhna)
- `run_linux_command` sirf ek chhoti allowlist (`security.ALLOWED_BINARIES`) chalata hai, shell metacharacters (`; | & $() `) hamesha block hain.
- Network-targeting tools (ping/curl/nmap/...) ke command me sirf `127.0.0.1`/`localhost` target ho sakta hai -- koi third-party host/IP scan allowed nahi.
- `scan_local_vulnerability()` koi host parameter leta hi nahi -- hamesha sirf apna 127.0.0.1 dekhta hai.
- `check_web_status(url)` kisi bhi public website ka status/headers dekh sakta hai (normal uptime-check jaisa), lekin private/internal (127.*, 10.*, 192.168.*, ...) addresses block hain, taaki isse LAN scan na ho.
- Ye sab defensive/monitoring tools hain -- koi exploit, password-cracking ya offensive tool yahan nahi hai, aur nahi hoga.
