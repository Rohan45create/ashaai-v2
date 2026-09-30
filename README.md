# AshaAI

## 💡 Introduction

<p align="center">
<img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781443006/asha_logo_nix7n7.jpg" width="50%" align="center" style="border-radius: 8px;">
</p>

**Problem Statement**: Data Driven Volunteer Coordination For Social Impact — India's 1 million+ ASHA (Accredited Social Health Activist) frontline health workers still rely on paper registers to track maternal health, child nutrition, vaccinations, disease surveillance, and orphan/vulnerable-child welfare across rural villages. This causes delayed detection of high-risk cases, double data entry, zero real-time visibility for supervisors, and disconnected coordination with NGOs that care for orphaned and vulnerable children.

**Solution**: AshaAI is a multilingual, AI-assisted health platform that digitizes ASHA worker operations end-to-end. Workers fill 12 health registers by voice in Marathi and Hindi, photograph paper registers for OCR import, and scan children for malnutrition using a photo plus a ₹5 coin as a size reference. A deterministic risk engine scores every child and flags high-risk pregnancies, supervisors get real-time oversight through a web portal, and a built-in **NGO & Orphanage Integration** module lets NGOs self-register through Google Forms, lets supervisors book visits and assign workers, and emails NGOs automatically. Everything is offline-first: data queues on the phone and syncs when connectivity returns. **AI only drafts and suggests — a human always taps Save before anything reaches the database.**

A **Google Solution Challenge 2026** Project — Selected in the **Global Top 100** — and a **Smart India Hackathon 2026** submission (PS ID: SIH26198) — Built by **Team AshaAI**.

## Intro To AshaAI Video

[![Intro To AshaAI](https://res.cloudinary.com/drb4gctam/image/upload/v1781581550/Empowering_ASHAs_Elevating_Healthcare_1_pe40sk.png)](https://youtu.be/ZbqG6uUldfI?si=LarbruBTeVuaWRxU)

A short walkthrough of AshaAI's voice-first, offline-first health platform for ASHA workers. Click the image above to watch the video.

### Our Target SDG Goals 🎯

<p align="center">
  <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781363044/E_PRINT_03_hnvufu.jpg" width="200"/>
  <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781362667/E_PRINT_10_xruhrt.jpg" width="200"/>
  <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781519319/E_PRINT_17_k36bty.jpg" width="200"/>
</p>

AshaAI enables early detection of high-risk maternal and child health cases through risk scoring and real-time monitoring, directly supporting
<ins>**SDG 3 — Good Health and Well-being**</ins>. Its NGO & Orphanage Integration module ensures equitable healthcare access for every child — connecting orphaned and vulnerable children with coordinated health visits and welfare support, regardless of where they live or who cares for them — advancing <ins>**SDG 10 — Reduced Inequalities**</ins>. And by building a seamless digital bridge between frontline ASHA workers, government health supervisors, and NGOs/orphanages — automating registration, scheduling, and communication that previously required manual coordination — AshaAI fosters the kind of cross-sector collaboration needed to deliver healthcare at scale, advancing <ins>**SDG 17 — Partnerships for the Goals**</ins>.


## 🌍 Global Recognition for ASHA Workers

> Global leaders and organizations consistently recognize ASHA workers as the indispensable backbone of rural healthcare in India. Their tireless dedication forms the foundation of community well-being, inspiring platforms like **AshaAI** to empower and streamline their vital mission.

<br>

<table style="width: 100%; text-align: center;">
  <tr>
    <td style="width: 33%; padding: 15px;">
      <img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSVJGdHjeqAg-GnIIqEVjdMYz9qnvbVzNDmUA&s" width="100%" style="border-radius: 8px;">
      <br><br>
      <b>Narendra Modi</b><br>
      <i>Prime Minister, India</i>
    </td>
    <td style="width: 33%; padding: 15px;">
      <img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRKvGH2pS1yFfwy4X2twnYuivQPMaiCF03F3A&s" width="100%" style="border-radius: 8px;">
      <br><br>
      <b>World Health Organization</b><br>
      <i>Global Health Leaders Award</i>
    </td>
    <td style="width: 33%; padding: 15px;">
      <img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSepURZt-f112wNUGblzXPMA_2fAGMKHVDZUg&s" width="100%" style="border-radius: 8px;">
      <br><br>
      <b>Bill Gates</b><br>
      <i>Co-Chair, Gates Foundation</i>
    </td>
  </tr>
  <tr style="vertical-align: top;">
    <td style="padding: 10px;">
      <p><i>"ASHA workers are at the forefront of ensuring a healthy India. Their dedication and determination is admirable."</i></p>
      <a href="https://www.pmindia.gov.in/en/news_updates/pm-expresses-happiness-on-the-entire-team-of-asha-workers-getting-who-director-generals-global-health-leaders-award/?comment=disable" target="_blank"><b>Read Statement ↗</b></a>
    </td>
    <td style="padding: 10px;">
      <p><i>Honored India's 1 million ASHA workers for their crucial, life-saving role in linking rural communities with the formal health system.</i></p>
      <a href="https://x.com/WHO/status/1528411437332369408" target="_blank"><b>View Official Tweet ↗</b></a>
    </td>
    <td style="padding: 10px;">
      <p><i>Views them as the vital "invisible structure" of global public health, serving as lifesavers bridging the gap to marginalized communities.</i></p>
      <a href="https://www.pib.gov.in/PressReleasePage.aspx?PRID=1546792&reg=48&lang=2" target="_blank"><b>Read Press Release ↗</b></a>
    </td>
  </tr>
</table>
<p align="center">
  <i>🌟 <b>And countless more...</b> Beyond these global figures, ASHA workers receive daily recognition from state health ministries, local NGOs, and the millions of rural families whose lives they touch. Their impact goes far beyond what can be captured in a single table, driving our commitment to build tools that genuinely support their groundwork.</i>
</p>

## 📚 Technical Architecture & Documentation

Want to dive deeper into how **AshaAI** works under the hood? We have compiled a comprehensive technical document that covers everything from our core algorithms to our database structure. The repository's `docs/` folder also holds the living design documents (`ARCHITECTURE.md`, `PRD.md`, `AI_AUTONOMY_POLICY.md`, `PHASES.md`, `RULES.md`, `MEMORY.md`).

**Inside the document, you will find:**
* ⚙️ **Complete Tech Stack:** Detailed breakdown of frontend, backend, AI service, and database technologies.
* 🧠 **Algorithms & Logic:** How our data syncing, offline support, risk engine, and orphan-flagging systems operate.
* 🏗️ **System Architecture:** How the ASHA worker module communicates with the supervisor portal and the NGO network.
* 🚀 **Implementation Guide:** Step-by-step insights into how we built and scaled the application.

<br>

<p align="center">
  <a href="https://drive.google.com/file/d/1z8rv3p6vAUzPKhGIpnf9wki-_R4Bv7rg/view?usp=sharing" target="_blank">
    <img src="https://img.shields.io/badge/📄_View_Full_Project_Documentation-0052CC?style=for-the-badge&logo=googledrive&logoColor=white" alt="View Documentation Button" />
  </a>
</p>

## 🗂 Repository Structure

```
ashaai-v2/
├── frontend/     React 19 PWA — ASHA app + Admin/Supervisor portal   → see frontend/README.md
├── backend/      Spring Boot 3 (Java 21) — the ONLY write path to the database → see backend/README.md
├── ai-service/   Python FastAPI — provider chain, malnutrition pipeline, voice & agent → see ai-service/README.md
├── docs/         Architecture, PRD, AI autonomy policy, phases, rules, memory log
├── scripts/      Azure provisioning + auth seeding helpers
└── .github/workflows/   CI + deploy workflows (frontend, backend, ai-service, cron)
```

**How the pieces talk to each other**

```
React PWA (ASHA + Admin)  ──►  Spring Boot backend  ──►  Supabase PostgreSQL (RLS, Flyway)
        │                            │
   IndexedDB offline queue           └──►  FastAPI ai-service  ──►  near.ai (optional)
                                                                  ► Hugging Face open models
                                                                  ► Gemini / Groq (final fallback)
Google Form → Apps Script webhook ──►  Spring Boot  /api/ngo/form-submission  ──►  Gmail SMTP
```

The one rule of the architecture: **React → Spring Boot → Postgres is the only write path.** The frontend never writes application data to Supabase directly (only `supabase.auth.*` calls), and no AI feature can submit, publish, delete, or send on its own.

## ⚙️ Local Development Setup

Each service is set up independently — follow the README inside the folder:

| Service | Setup guide | Default local port |
|---|---|---|
| Backend (Spring Boot) | [`backend/README.md`](backend/README.md) | `8080` |
| AI service (FastAPI) | [`ai-service/README.md`](ai-service/README.md) | `8000` |
| Frontend (React + Vite) | [`frontend/README.md`](frontend/README.md) | `5173` |

Recommended start order: **backend → ai-service → frontend**.

```bash
git clone https://github.com/Rohan45create/ashaai-v2.git
cd ashaai-v2
```

> [!WARNING]
> Never commit `.env` / `.env.local` files or paste real keys into workflow files. Runtime secrets live only in your local env files, GitHub Actions secrets, and Azure app settings.

## ☁️ Deployment (all free-tier)

| Component | Hosted on | Deployed by |
|---|---|---|
| Frontend | Azure Static Web Apps (Free) | `.github/workflows/deploy-frontend.yml` |
| Backend | Azure App Service (F1 Free) | `.github/workflows/deploy-backend.yml` |
| AI service | Azure Container Apps (scale-to-zero) | `.github/workflows/deploy-ai-service.yml` |
| Database & Auth | Supabase | Flyway migrations run by the backend |
| Scheduled jobs | GitHub Actions cron | `.github/workflows/cron-jobs.yml` |

Deploy order: **ai-service → backend → frontend**, then point the NGO Apps Script webhooks at the backend URL.

## 🚀 Getting Started

> [!IMPORTANT]
> AshaAI is offline-first — it works without an internet connection and syncs automatically when connectivity returns.

1. Open the live prototype: [https://orange-plant-04dddc700.4.azurestaticapps.net](https://orange-plant-04dddc700.4.azurestaticapps.net)
2. ASHA Worker Login: Phone number + OTP (test credentials available)
3. Admin / Supervisor Login:
   Email: *admin@asha.gov.in*
   Password: *(provided in demo video / on request)*

## ⚠️ Initial Survey and Problem Statement Research

<table style="width: 100%;">
  <tr>
    <td align="center" width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781433793/img_5355.mov_2026-06-14_13-07-04.187_orqa2v.jpg" alt="Asha Center" width="350"/><br>
      <p>Asha Center</p>
    </td>
    <td align="center" width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781434866/img_5358_1_.mov_2026-06-14_16-23-44.861_lrhavt.jpg" alt="Asha Center" width="350"/><br>
      <p>Asha Center</p>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781434872/img_5352.mov_2026-06-14_16-24-55.207_be27c6.jpg" alt="Asha Center" width="350"/><br>
      <p>Asha Center</p>
    </td>
    <td align="center" width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781434874/WhatsApp_Image_2026-06-14_at_4.24.49_PM_wlslic.jpg" alt="MNC Letter" width="120"/><br>
      <p>Municipal Corporation Letter</p>
    </td>
  </tr>
  <tr>
    <td colspan="2">
      <p>📌 We conducted in-depth interviews with practicing ASHA workers in the Marathwada region and photographed their actual physical diary registers — a 128-page book covering 75 different task types across village health surveys, family surveys, performance tasks, and training records.</p>
      <p>📝 Our findings revealed major pain points: no edit option once data is written (one mistake = permanent error), apps that crash without internet, double work (paper diary then retype in an app), low digital literacy making typing difficult, and no structured way to coordinate with local NGOs caring for orphaned children. These insights fundamentally shaped AshaAI — shifting it from a narrow malnutrition-tracking app into a comprehensive 75-task digital work platform with built-in NGO coordination.</p>
    </td>
  </tr>
</table>

## 📊 Real User Research

#### In-person interviews with ASHA workers + analysis of physical register pages from real villages in Beed district, Maharashtra.

<hr>

<table style="width: 100%;">
  <tr>
    <p>➡ Our research highlighted that existing digital health apps for ASHA workers are English-only, require typing, depend entirely on internet connectivity, and have no integration with the NGO/orphanage network that supports vulnerable children in the same villages.</p>
    <p>➡ We mapped every column of the physical Family Survey Register (कुटुंब पाहणी सर्वेक्षण) and Village Health Survey Register (ग्राम आरोग्य सर्वेक्षण) field-by-field to ensure AshaAI's digital forms are a 1:1 replacement for the paper register ASHA workers already know — and extended this with a dedicated orphan/vulnerable-child flagging system feeding directly into our NGO module.</p>
  </tr>
</table>

### 📈 Public Opinion & Community Needs

**Surveyed 1000+ community members and healthcare workers across Maharashtra to understand core field challenges.**

➡ Overall, our research highlighted the urgency of addressing offline data entry, and AshaAI emerged as a solution-driven platform bringing Marathi and Hindi localization directly to the workers.

➡ We also conducted a survey to get community responses for the most sought-after features listed below.

<table style="width: 100%;">
  <tr>
    <td width="100%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436523/Screenshot_14-6-2026_165742_docs.google.com_souyhg.jpg" alt="Survey Result: Overview" style="width: 100%;">
    </td>
  </tr>
</table>
<table style="width: 100%;">
  <tr>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436513/Screenshot_14-6-2026_165532_docs.google.com_yrerdl.jpg" alt="Survey Result 1" style="width: 100%;">
    </td>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436514/Screenshot_14-6-2026_165542_docs.google.com_hc97hz.jpg" alt="Survey Result 2" style="width: 100%;">
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436513/Screenshot_14-6-2026_16572_docs.google.com_jaa2fm.jpg" alt="Survey Result 3" style="width: 100%;">
    </td>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436514/Screenshot_14-6-2026_165553_docs.google.com_a3tzn5.jpg" alt="Survey Result 4" style="width: 100%;">
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436515/Screenshot_14-6-2026_165625_docs.google.com_tgf4i8.jpg" alt="Survey Result 5" style="width: 100%;">
    </td>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436514/Screenshot_14-6-2026_165617_docs.google.com_jbf5py.jpg" alt="Survey Result 6" style="width: 100%;">
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436516/Screenshot_14-6-2026_165641_docs.google.com_fytydj.jpg" alt="Survey Result 7" style="width: 100%;">
    </td>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436517/Screenshot_14-6-2026_165652_docs.google.com_nn0ut2.jpg" alt="Survey Result 8" style="width: 100%;">
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436518/Screenshot_14-6-2026_165712_docs.google.com_csiboj.jpg" alt="Survey Result 9" style="width: 100%;">
    </td>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436518/Screenshot_14-6-2026_165633_docs.google.com_weuall.jpg" alt="Survey Result 10" style="width: 100%;">
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436520/Screenshot_14-6-2026_165725_docs.google.com_p041o4.jpg" alt="Survey Result 11" style="width: 100%;">
    </td>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436520/Screenshot_14-6-2026_165733_docs.google.com_aj77qr.jpg" alt="Survey Result 12" style="width: 100%;">
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436522/Screenshot_14-6-2026_16561_docs.google.com_t9mil1.jpg" alt="Survey Result 13" style="width: 100%;">
    </td>
    <td width="50%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781436522/Screenshot_14-6-2026_16569_docs.google.com_iqpbwp.jpg" alt="Survey Result 14" style="width: 100%;">
    </td>
  </tr>
  <tr>
    <td width="50%">
      <a href="https://docs.google.com/spreadsheets/d/1U9nKSXWsRMeCXmQv4kX1wksEwEVVB6ZDDZEPAmejlRk/edit?usp=sharing" target="_blank">
        <img src="https://i.ibb.co/pvF3sTkm/image.png" alt="AshaAI Feedbacks Excel Sheet" style="width: 100%;">
      </a>
      <br>
      <b>AshaAI FeedBacks Excel Sheet</b>
    </td>
  </tr>
</table>

## 🛳 User Guide

### Walkthrough

<table style="width: 100%;">
  <tr>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360961/Galaxy-Note20-Ultra-ashaai-prod.web.app_wj0zdt.png" width="120"/><br>
      <b>ASHA Login Screen</b><br>
      Phone OTP login with EN / मर / हि language toggle always visible.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360956/Galaxy-Note20-Ultra-ashaai-prod.web.app_1_clwpwf.png" width="120"/><br>
      <b>ASHA Home Dashboard</b><br>
      Today's families, critical cases, priority list, and quick access to all 12 health modules.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360960/Galaxy-Note20-Ultra-ashaai-prod.web.app_9_a7y8uk.png" width="120"/><br>
      <b>Voice Dictation</b><br>
      Tap the mic and speak in Marathi/Hindi — the AI structures the data into form fields for the worker to review.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781431028/Galaxy-Note20-Ultra-ashaai-prod.web.app2_2_lb3rwz.png" width="120"/><br>
      <b>Register OCR Import</b><br>
      Photograph a paper register page — vision AI extracts every row, low-confidence fields are flagged for review before import.
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360961/Galaxy-Note20-Ultra-ashaai-prod.web.app_13_e8b9dk.png" width="120"/><br>
      <b>AI Malnutrition Scan</b><br>
      Photograph a child with a ₹5 coin for scale — a 3-model pipeline measures, classifies, and drafts a SAM/MAM/Normal report for the worker to confirm.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360956/Galaxy-Note20-Ultra-ashaai-prod.web.app_6_lgk9n0.png" width="120"/><br>
      <b>Ask AshaAI</b><br>
      Conversational health Q&A assistant for protocols, danger signs, and guidance.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360960/Galaxy-Note20-Ultra-ashaai-prod.web.app_14_dthump.png" width="120"/><br>
      <b>Download Survey Registers</b><br>
      ASHA workers can export any register as a report for a custom date range.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360961/Macbook-Air-ashaai-prod.web.app_1_f6jbmc.png" width="300"/><br>
      <b>Admin Dashboard</b><br>
      Real-time view of all ASHA workers, families covered, critical cases, and survey completion.
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360968/Macbook-Air-ashaai-prod.web.app_7_bu3xri.png" width="300"/><br>
      <b>Pending Review Queue</b><br>
      Supervisors review flagged alerts (validation flags, high-risk ANC, NGO registrations) and approve/reject with one tap.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360969/Macbook-Air-ashaai-prod.web.app_8_akgzck.png" width="300"/><br>
      <b>Survey Builder</b><br>
      Supervisors create new survey fields with auto-translation to Marathi & Hindi and publish to all workers instantly.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360964/Macbook-Air-ashaai-prod.web.app_4_lrhvky.png" width="300"/><br>
      <b>NRC Referral Tracking</b><br>
      Track malnutrition referrals from pending → admitted → discharged with full case history.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360967/Macbook-Air-ashaai-prod.web.app_9_jrzweo.png" width="300"/><br>
      <b>Coverage Map</b><br>
      Google Maps-powered village-level view showing coverage and critical cases across the district.
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360964/Macbook-Air-ashaai-prod.web.app_6_xwtevu.png" width="300"/><br>
      <b>NGO Management</b><br>
      Supervisors view all registered NGOs/orphanages, approve new registrations, and manage their details.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781360956/Galaxy-Note20-Ultra-ashaai-prod.web.app_3_yeteie.png" width="120"/><br>
      <b>NGO Appointment Booking</b><br>
      Schedule visits to an NGO/orphanage and assign specific workers, with automatic email confirmation.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781431029/Galaxy-Note20-Ultra-docs.google.com_d6mht4.png" width="120"/><br>
      <b>NGO Self-Registration (Google Forms)</b><br>
      New NGOs register themselves via a Google Form — submissions flow into the admin's pending review queue automatically.
    </td>
    <td align="center" width="25%">
      <img src="https://res.cloudinary.com/drb4gctam/image/upload/v1781431015/Screenshot_2026-06-14_152019_tz3943.png" width="300"/><br>
      <b>Automated NGO Emails</b><br>
      NGOs receive professional email notifications for scheduled visits, with a one-click reschedule link.
    </td>
  </tr>
</table>

## Key Features:

- **Offline-first architecture** — fully functional with zero connectivity; an IndexedDB queue and Service Worker sync automatically when online.
- **Voice dictation in Marathi & Hindi** on every form, via a provider chain: near.ai (optional) → AI4Bharat Indic-Whisper → Gemini.
- **Conversational Agent** — one tap starts a continuous spoken session: fill a survey by talking (draft only, worker confirms) or ask for a PDF report by voice.
- **Register OCR import** — digitizes handwritten paper registers row-by-row with confidence scoring.
- **AI Malnutrition Scanner (3-model pipeline)** — MediaPipe + ₹5-coin measurement, a custom ONNX classifier, and a Gemini + Groq consensus report; the worker must confirm the grade before it saves.
- **Deterministic Predictive Risk Engine** — scores every child 0–100 from real signals (no AI guess), plus genetic-risk augmentation and a supervisor-confirmed high-risk ANC flag.
- **Priority List** — merges risk scores, overdue visits/vaccines, and supervisor flags into one ranked list per worker.
- **12 health modules, one generic renderer** — all modules (and supervisor-built custom surveys) are database-driven schemas rendered by a single dynamic form.
- **Full edit capability** on every field with an append-only audit trail.
- **Dynamic Survey Builder** — supervisors publish new surveys without code, auto-translated to Marathi & Hindi.
- **Permanent-UUID identity** — Aadhaar or temporary ID is only a lookup key; Aadhaar card scanning is on-device OCR only and the image never leaves the phone. Aadhaar/ABHA fields are AES-256-GCM encrypted.
- **Human-in-the-loop AI** — every AI feature drafts and suggests; no agent has a submit/publish/delete/send tool.
- **NGO & Orphanage Integration** — NGOs self-register via Google Forms, supervisors approve registrations, book visits, assign ASHA workers, and the system emails the NGO with visit details and a pre-filled reschedule link.
- **Real-time supervisor portal** with dashboard, worker activity, NRC referral tracking, coverage map, and PDF/CSV reports.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite, Tailwind CSS, Zustand, PWA (vite-plugin-pwa), IndexedDB offline queue |
| Backend | Spring Boot 3.5, Java 21, Spring Data JPA, Spring Security (Supabase JWKS), Flyway |
| AI microservice | Python FastAPI, Pydantic v2, onnxruntime, MediaPipe |
| AI providers | near.ai (optional) → Hugging Face open models (Indic-Whisper, Sarvam-30B, IndicTrans2) → Gemini / Groq |
| Database & Auth | Supabase PostgreSQL with Row-Level Security, Supabase Auth (Phone OTP for ASHA, Email/Password for admin) |
| NGO automation | Google Forms + Apps Script webhook, Gmail SMTP |
| Maps | Google Maps JavaScript API |
| Hosting & CI/CD | Azure Static Web Apps, App Service, Container Apps; GitHub Actions |

