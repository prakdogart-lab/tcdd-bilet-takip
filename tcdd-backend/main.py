"""
TCDD Bilet Takip Backend
Render.com'a ücretsiz deploy edilebilir.
"""
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import asyncio, httpx, json, os, time, logging
from datetime import datetime
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="TCDD Bilet Takip API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── In-memory store (production'da Redis/PostgreSQL kullan) ───
trackings: dict = {}          # id -> TrackingJob
notification_tokens: dict = {}  # device_id -> fcm_token

scheduler = AsyncIOScheduler()

# ─── TCDD API config ───
TCDD_BASE = "https://gise.tcddtasimacilik.gov.tr"
TCDD_HEADERS = {
    "Content-Type": "application/json",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "tr-TR,tr;q=0.9",
    "Origin": TCDD_BASE,
    "Referer": TCDD_BASE + "/",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
}

# ─── Pydantic models ───

class TrackingRequest(BaseModel):
    device_id: str
    fcm_token: str
    kalkis_istasyon_id: int
    kalkis_istasyon_adi: str
    varis_istasyon_id: int
    varis_istasyon_adi: str
    sefer_tarihi: str          # "2025-06-15"
    tren_no: str
    tren_adi: str
    kalkis_saati: str          # "08:00"
    varis_saati: str
    seat_types: List[str]      # ["EKONOMI", "BUSINESS", "TEKERLEKLI"]
    auto_book: bool = False

class TokenUpdate(BaseModel):
    device_id: str
    fcm_token: str

class SeferSorguRequest(BaseModel):
    kalkis_istasyon_id: int
    varis_istasyon_id: int
    tarih: str                 # "2025-06-15"

# ─── TCDD API calls ───

async def tcdd_get_stations() -> list:
    async with httpx.AsyncClient(timeout=30) as client:
        try:
            r = await client.get(
                f"{TCDD_BASE}/api/seferler/istasyon-liste",
                headers=TCDD_HEADERS
            )
            r.raise_for_status()
            return r.json()
        except Exception as e:
            logger.error(f"Station fetch error: {e}")
            return []

async def tcdd_search_trains(kalkis_id: int, varis_id: int, tarih: str) -> list:
    payload = {
        "kalkisIstasyonId": kalkis_id,
        "varisIstasyonId": varis_id,
        "binisIstasyonId": kalkis_id,
        "inisIstasyonId": varis_id,
        "yolcuSayisi": 1,
        "seyahatTipi": 1,
        "aktarmaVar": False,
        "sinifBilgisi": [{"yolcuTipiId": 0, "yolcuSayisi": 1}],
        "tarih": f"{tarih} 00:00:00"
    }
    async with httpx.AsyncClient(timeout=30, follow_redirects=True) as client:
        for endpoint in [
            "/api/seferler/sefer-sorgulama-v2",
            "/api/seferler/sefer-sorgulama",
        ]:
            try:
                r = await client.post(
                    f"{TCDD_BASE}{endpoint}",
                    json=payload,
                    headers=TCDD_HEADERS
                )
                if r.status_code == 200:
                    data = r.json()
                    seferler = data.get("seferlerDto") or data.get("data") or []
                    logger.info(f"Found {len(seferler)} trains for {kalkis_id}->{varis_id} on {tarih}")
                    return seferler
            except Exception as e:
                logger.warning(f"Endpoint {endpoint} failed: {e}")
                continue
    return []

def check_seat_availability(sefer: dict, seat_types: List[str]) -> List[str]:
    """Returns list of available seat type names from requested types."""
    vagonlar = sefer.get("vagonTipleri") or []
    available = []

    for seat_type in seat_types:
        found = False
        for vagon in vagonlar:
            bos = vagon.get("bos", 0)
            vagon_adi = vagon.get("vagonTipAdi", "").upper()
            is_tekerlekli = vagon.get("tekerlekliSandalye", False)

            if bos <= 0:
                continue

            if seat_type == "EKONOMI" and not is_tekerlekli and "EKONOMİ" in vagon_adi:
                found = True
            elif seat_type == "BUSINESS" and ("BUSİNESS" in vagon_adi or "BUSINESS" in vagon_adi):
                found = True
            elif seat_type == "TEKERLEKLI" and is_tekerlekli:
                found = True

        if found:
            available.append(seat_type)

    return available

async def send_fcm_notification(fcm_token: str, title: str, body: str, data: dict = None):
    """Send FCM push notification via Firebase HTTP v1 API."""
    fcm_server_key = os.environ.get("FCM_SERVER_KEY", "")
    if not fcm_server_key:
        logger.warning("FCM_SERVER_KEY not set — skipping push notification")
        return

    payload = {
        "to": fcm_token,
        "notification": {
            "title": title,
            "body": body,
            "sound": "alarm",
            "android_channel_id": "tcdd_alarm"
        },
        "data": data or {},
        "priority": "high",
        "android": {
            "priority": "high",
            "notification": {
                "channel_id": "tcdd_alarm",
                "notification_priority": "PRIORITY_MAX",
                "sound": "alarm",
                "vibrate_timings": ["0s", "1s", "0.5s", "1s"]
            }
        }
    }

    async with httpx.AsyncClient(timeout=15) as client:
        try:
            r = await client.post(
                "https://fcm.googleapis.com/fcm/send",
                json=payload,
                headers={
                    "Authorization": f"key={fcm_server_key}",
                    "Content-Type": "application/json"
                }
            )
            logger.info(f"FCM response: {r.status_code} {r.text}")
        except Exception as e:
            logger.error(f"FCM send error: {e}")

# ─── Background checker ───

async def check_all_trackings():
    """Called every 5 minutes by scheduler."""
    logger.info(f"[{datetime.now()}] Checking {len(trackings)} active trackings...")

    for tracking_id, job in list(trackings.items()):
        if not job.get("active", True):
            continue

        try:
            seferler = await tcdd_search_trains(
                job["kalkis_istasyon_id"],
                job["varis_istasyon_id"],
                job["sefer_tarihi"]
            )

            hedef_sefer = None
            for sefer in seferler:
                tren_no = sefer.get("trenNo", "")
                kalkis_tarih = sefer.get("binisTarih", "")
                if tren_no == job["tren_no"] or job["kalkis_saati"] in kalkis_tarih:
                    hedef_sefer = sefer
                    break

            if hedef_sefer is None:
                logger.info(f"Train {job['tren_no']} not found in results")
                trackings[tracking_id]["last_checked"] = int(time.time())
                continue

            available = check_seat_availability(hedef_sefer, job["seat_types"])

            trackings[tracking_id]["last_checked"] = int(time.time())
            trackings[tracking_id]["last_result"] = available

            if available:
                seat_names = {
                    "EKONOMI": "Ekonomi",
                    "BUSINESS": "Business",
                    "TEKERLEKLI": "Tekerlekli Sandalye"
                }
                available_text = ", ".join(seat_names.get(s, s) for s in available)

                title = f"🚆 YER AÇILDI! {job['tren_adi']}"
                body = (
                    f"{job['kalkis_istasyon_adi']} → {job['varis_istasyon_adi']}\n"
                    f"Tarih: {job['sefer_tarihi']} {job['kalkis_saati']}\n"
                    f"Müsait yer: {available_text}"
                )

                await send_fcm_notification(
                    fcm_token=job["fcm_token"],
                    title=title,
                    body=body,
                    data={
                        "tracking_id": tracking_id,
                        "tren_no": job["tren_no"],
                        "available_types": json.dumps(available)
                    }
                )
                logger.info(f"Notification sent for {job['tren_adi']}: {available}")

        except Exception as e:
            logger.error(f"Check failed for {tracking_id}: {e}")

# ─── API endpoints ───

@app.on_event("startup")
async def startup():
    scheduler.add_job(
        check_all_trackings,
        IntervalTrigger(minutes=5),
        id="check_trains",
        replace_existing=True
    )
    scheduler.start()
    logger.info("Scheduler started — checking every 5 minutes")

@app.on_event("shutdown")
async def shutdown():
    scheduler.shutdown()

@app.get("/")
def root():
    return {"status": "ok", "service": "TCDD Bilet Takip", "version": "1.0"}

@app.get("/health")
def health():
    return {
        "status": "ok",
        "active_trackings": len([j for j in trackings.values() if j.get("active")]),
        "total_trackings": len(trackings),
        "scheduler_running": scheduler.running
    }

@app.post("/token")
def update_token(req: TokenUpdate):
    notification_tokens[req.device_id] = req.fcm_token
    return {"status": "ok"}

@app.get("/stations")
async def get_stations():
    stations = await tcdd_get_stations()
    if not stations:
        # Fallback list
        stations = [
            {"istasyonAdi": "Ankara Gar", "istasyonId": 785},
            {"istasyonAdi": "İstanbul(Söğütlüçeşme)", "istasyonId": 99849},
            {"istasyonAdi": "İstanbul(Halkalı)", "istasyonId": 99848},
            {"istasyonAdi": "İzmir(Basmane)", "istasyonId": 783},
            {"istasyonAdi": "İzmir(Alsancak)", "istasyonId": 90061},
            {"istasyonAdi": "Eskişehir", "istasyonId": 810},
            {"istasyonAdi": "Konya", "istasyonId": 812},
            {"istasyonAdi": "Kayseri", "istasyonId": 800},
            {"istasyonAdi": "Sivas", "istasyonId": 780},
            {"istasyonAdi": "Malatya", "istasyonId": 802},
            {"istasyonAdi": "Diyarbakır", "istasyonId": 795},
            {"istasyonAdi": "Erzurum", "istasyonId": 806},
            {"istasyonAdi": "Kars", "istasyonId": 808},
            {"istasyonAdi": "Adana", "istasyonId": 784},
            {"istasyonAdi": "Gaziantep", "istasyonId": 796},
            {"istasyonAdi": "Afyon", "istasyonId": 779},
            {"istasyonAdi": "Denizli", "istasyonId": 793},
            {"istasyonAdi": "Bandırma", "istasyonId": 787},
            {"istasyonAdi": "Balıkesir", "istasyonId": 786},
            {"istasyonAdi": "Manisa", "istasyonId": 801},
            {"istasyonAdi": "Kütahya", "istasyonId": 799},
            {"istasyonAdi": "Uşak", "istasyonId": 819},
        ]
    return stations

@app.post("/search-trains")
async def search_trains(req: SeferSorguRequest):
    seferler = await tcdd_search_trains(req.kalkis_istasyon_id, req.varis_istasyon_id, req.tarih)
    return {"seferler": seferler, "count": len(seferler)}

@app.get("/trackings/{device_id}")
def get_trackings(device_id: str):
    device_trackings = {
        k: v for k, v in trackings.items()
        if v.get("device_id") == device_id
    }
    return list(device_trackings.values())

@app.post("/trackings")
async def add_tracking(req: TrackingRequest, background_tasks: BackgroundTasks):
    tracking_id = f"{req.device_id}_{req.tren_no}_{req.sefer_tarihi}_{int(time.time())}"

    trackings[tracking_id] = {
        "id": tracking_id,
        "device_id": req.device_id,
        "fcm_token": req.fcm_token,
        "kalkis_istasyon_id": req.kalkis_istasyon_id,
        "kalkis_istasyon_adi": req.kalkis_istasyon_adi,
        "varis_istasyon_id": req.varis_istasyon_id,
        "varis_istasyon_adi": req.varis_istasyon_adi,
        "sefer_tarihi": req.sefer_tarihi,
        "tren_no": req.tren_no,
        "tren_adi": req.tren_adi,
        "kalkis_saati": req.kalkis_saati,
        "varis_saati": req.varis_saati,
        "seat_types": req.seat_types,
        "auto_book": req.auto_book,
        "active": True,
        "created_at": int(time.time()),
        "last_checked": 0,
        "last_result": []
    }

    # Immediately do a first check
    background_tasks.add_task(check_all_trackings)

    return {"status": "ok", "tracking_id": tracking_id}

@app.delete("/trackings/{tracking_id}")
def delete_tracking(tracking_id: str):
    if tracking_id in trackings:
        del trackings[tracking_id]
        return {"status": "deleted"}
    raise HTTPException(status_code=404, detail="Tracking not found")

@app.patch("/trackings/{tracking_id}/toggle")
def toggle_tracking(tracking_id: str, active: bool):
    if tracking_id not in trackings:
        raise HTTPException(status_code=404, detail="Not found")
    trackings[tracking_id]["active"] = active
    return {"status": "ok", "active": active}

@app.post("/trackings/check-now")
async def check_now():
    await check_all_trackings()
    return {"status": "checked", "count": len(trackings)}
