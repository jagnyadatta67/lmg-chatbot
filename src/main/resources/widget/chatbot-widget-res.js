;(() => {
  const scriptTag =
    document.currentScript || Array.from(document.querySelectorAll('script[src*="chatbot-widget.js"]')).pop()

  // --- Config ---
  // All values are read from data-* attributes on the <script> tag, falling back to window.CHATBOT_CONFIG.
  // Example: <script src="chatbot-widget.js" data-backend="https://api.example.com/api/chat" data-concept="LIFESTYLE" ...>
  const config = {
    backend: scriptTag?.getAttribute("data-backend") || window.CHATBOT_CONFIG?.backend || "http://localhost:8080/api/chat",
    userid: scriptTag?.getAttribute("data-userid") || window.CHATBOT_CONFIG?.userid || "UNKNOWN_USER",
    concept: (scriptTag?.getAttribute("data-concept") || window.CHATBOT_CONFIG?.concept || "LIFESTYLE").toUpperCase(),
    appid: scriptTag?.getAttribute("data-appid") || window.CHATBOT_CONFIG?.appid || "UNKNOWN_APP",
    env: scriptTag?.getAttribute("data-env") || window.CHATBOT_CONFIG?.env || "uat5",
    giftcardEnv: scriptTag?.getAttribute("data-env") || window.CHATBOT_CONFIG?.env || "uat5",
    apikey: scriptTag?.getAttribute("X-API-Key") || window.CHATBOT_CONFIG?.apikey || "",
  }

  console.log("💎 Chatbot Config:", config)

  // --- Session state ---
  // Populated by resolveSession() on widget load. All API calls read from here.
  const session = { customerId: "anonymous", accessToken: null, profile: null }
  let profileCachePromise = null   // tracks the in-flight profile fetch

  async function resolveSession() {
    const rawToken = config.userid
    if (!rawToken || rawToken === "UNKNOWN_USER") return

    // sessionStorage cache — keyed by concept + raw token so different
    // brands / users on the same browser never share a cached result.
    const cacheKey = `chatbot_session_${config.concept}_${rawToken}`
    const cached = sessionStorage.getItem(cacheKey)
    if (cached) {
      try {
        const parsed = JSON.parse(cached)
        session.customerId  = parsed.customerId  || "anonymous"
        session.accessToken = parsed.accessToken || null
        console.log("💎 Session restored from cache:", session.customerId)
        profileCachePromise = fetchProfileCache()
        return
      } catch { /* corrupted entry — fall through to API call */ }
    }

    // Derive the backend base URL from config.backend
    // e.g. "http://localhost:8080/api/chat" → "http://localhost:8080"
    const backendBase = config.backend.replace(/\/api\/chat.*$/, "")
    const url = `${backendBase}/api/user/token-details`

    try {
      const res = await fetch(url, {
        method:  "POST",
        headers: {
          "Content-Type": "application/json",
          "X-API-Key":    config.apikey,
        },
        body: JSON.stringify({
          token:   rawToken,
          concept: config.concept,
          env:     config.env,
          appId:   config.appid,
        }),
      })

      if (!res.ok) throw new Error(`HTTP ${res.status}`)

      const data = await res.json()
      // uid       = field name in UserWsDTO (Spring backend)
      // customerId = field name from raw brand API response
      // access_token / accessToken = both covered (snake_case from brand API, camelCase from backend)
      session.customerId  = data.uid || data.customerId || "anonymous"
      session.accessToken = data.accessToken || data.access_token || null

      sessionStorage.setItem(cacheKey, JSON.stringify({
        customerId:  session.customerId,
        accessToken: session.accessToken,
      }))
      console.log("💎 Session resolved:", session.customerId)

      // Pre-fetch profile immediately so "My Profile" renders from cache instantly
      profileCachePromise = fetchProfileCache()
    } catch (err) {
      console.warn("⚠️ Token resolution failed, using anonymous:", err.message)
    }
  }

  async function fetchProfileCache() {
    if (!session.accessToken) return
    try {
      const backendBase = config.backend.replace(/\/api\/chat.*$/, "")
      const res = await fetch(`${backendBase}/api/user/profile`, {
        method:  "POST",
        headers: { "Content-Type": "application/json", "X-API-Key": config.apikey },
        body: JSON.stringify({
          token:   session.accessToken,
          concept: config.concept,
          env:     config.env,
          appId:   config.appid,
        }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      if (data) {
        session.profile = data
        console.log("💎 Profile cached for:", session.customerId)
      }
    } catch (err) {
      console.warn("⚠️ Profile cache fetch failed:", err.message)
    }
  }

  // --- Brand Themes ---
  const BRAND_THEMES = {
    LIFESTYLE: {
      primary: "#F89F17",
      secondary: "#FFB84D",
      dark: "#1a1a1a",
      light: "#f8f9fa",
      logo: "https://assets-cloud.landmarkshops.in/website_images/static-pages/brand_exp/brand2images/logos/prod/lifestyle-logo-136x46.svg",
    },
    MAX: {
      primary: "#D1AC88",
      secondary: "#b8916a",
      dark: "#1a1a1a",
      light: "#ffffff",
      headerBg: "#ffffff",
      headerText: "#1a1a1a",
      headerBorder: "0 0 1px 0",
      headerBorderColor: "#D1AC88",
      containerBorder: "1px solid #D1AC88",
      logo: "https://assets-cloud.landmarkshops.in/website_images/in/logos/new-max-logo-90x40.svg",
    },
    BABYSHOP: {
      primary: "#819F83",
      secondary: "#9FC19F",
      dark: "#1a1a1a",
      light: "#f8f9fa",
      logo: "https://assets-cloud.landmarkshops.in/website_images/in/logos/logo-babyshop.svg",
    },
    HOMECENTRE: {
      primary: "#7665A0",
      secondary: "#9988C4",
      dark: "#1a1a1a",
      light: "#f8f9fa",
      logo: "https://assets-cloud.landmarkshops.in/website_images/in/logos/new-logo-homecentre.svg",
    },
  }

  const theme = BRAND_THEMES[config.concept] || BRAND_THEMES.LIFESTYLE

  function injectStyles() {
    const styleId = "chatbot-widget-styles"
    if (document.getElementById(styleId)) return

    const style = document.createElement("style")
    style.id = styleId
    style.textContent = `
      /* === CHATBOT WIDGET STYLES — RESPONSIVE === */
      #chatbot-button, #chatbot-container, #chatbot-container * {
        box-sizing: border-box;
      }

      /* ── FAB Button ─────────────────────────────────────────────────────── */
      #chatbot-button {
        position: fixed;
        bottom: 28px;
        right: 28px;
        width: 64px;
        height: 64px;
        border-radius: 50%;
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        border: none;
        cursor: pointer;
        z-index: 10000;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 6px 24px rgba(0,0,0,0.18), 0 2px 8px rgba(0,0,0,0.12);
        transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
        overflow: visible;
      }
      #chatbot-button:hover  { transform: scale(1.08); box-shadow: 0 10px 32px rgba(0,0,0,0.22); }
      #chatbot-button:active { transform: scale(0.94); }

      .chat-fab-icon {
        width: 78%; height: 78%;
        display: flex; align-items: center; justify-content: center;
        background: rgba(255,255,255,0.92);
        border-radius: 50%;
        padding: 6px;
      }
      .chat-fab-icon img {
        width: 100%; height: 100%;
        object-fit: contain;
        /* No color filter — show logo in original brand colors */
      }

      .chat-fab-badge {
        position: absolute;
        top: -4px; right: -4px;
        background: #ff4757;
        color: white;
        border-radius: 50%;
        width: 20px; height: 20px;
        display: flex; align-items: center; justify-content: center;
        font-size: 11px; font-weight: 700;
        border: 2px solid white;
        animation: pulse 2s infinite;
      }
      @keyframes pulse {
        0%,100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(255,71,87,0.4); }
        50%      { transform: scale(1.1); box-shadow: 0 0 0 6px rgba(255,71,87,0); }
      }

      /* ── Chat Window — Desktop ───────────────────────────────────────────── */
      #chatbot-container {
        position: fixed;
        bottom: 108px;
        right: 28px;
        width: min(92vw, 420px);
        height: min(82vh, 660px);
        background: #ffffff;
        border-radius: 20px;
        border: ${theme.containerBorder || "none"};
        box-shadow: 0 20px 60px rgba(0,0,0,0.18), 0 4px 16px rgba(0,0,0,0.08);
        display: none;
        flex-direction: column;
        overflow: hidden;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
        z-index: 9999;
      }
      #chatbot-container.open {
        display: flex;
        animation: desktopSlideUp 0.32s cubic-bezier(0.34,1.36,0.64,1);
      }
      @keyframes desktopSlideUp {
        from { opacity: 0; transform: translateY(24px) scale(0.97); }
        to   { opacity: 1; transform: translateY(0)   scale(1); }
      }

      /* ── Mobile: Full-screen bottom sheet ──────────────────────────────── */
      @media (max-width: 768px) {
        #chatbot-button { bottom: 20px; right: 20px; width: 58px; height: 58px; }
        .chat-fab-icon img { width: 36px; height: 36px; }

        #chatbot-container {
          bottom: 0 !important; right: 0 !important;
          width: 100% !important;
          height: 92dvh !important;
          max-height: 92dvh !important;
          border-radius: 20px 20px 0 0 !important;
          box-shadow: 0 -8px 40px rgba(0,0,0,0.18) !important;
        }
        #chatbot-container.open {
          animation: sheetSlideUp 0.38s cubic-bezier(0.34,1.2,0.64,1);
        }
        @keyframes sheetSlideUp {
          from { transform: translateY(100%); opacity: 0.6; }
          to   { transform: translateY(0);    opacity: 1; }
        }
        .chat-drag-handle { display: flex !important; }
      }
      @media (max-width: 360px) {
        #chatbot-container { height: 96dvh !important; }
      }

      /* ── Drag Handle (mobile only) ──────────────────────────────────────── */
      .chat-drag-handle {
        display: none;
        justify-content: center;
        padding: 10px 0 4px;
        background: white;
        flex-shrink: 0;
        cursor: grab;
      }
      .chat-drag-handle-bar {
        width: 40px; height: 4px;
        background: #d1d5db;
        border-radius: 2px;
      }

      /* ── Header ─────────────────────────────────────────────────────────── */
      .chat-header {
        background: ${theme.headerBg || `linear-gradient(135deg, ${theme.primary} 0%, ${theme.secondary} 100%)`};
        color: ${theme.headerText || "white"};
        border-bottom: ${theme.headerBorderColor ? `2px solid ${theme.headerBorderColor}` : "none"};
        padding: 14px 16px 14px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-shrink: 0;
        position: relative;
      }
      .chat-header-left {
        display: flex; align-items: center; gap: 10px;
      }
      .chat-header-avatar {
        width: 40px; height: 40px;
        background: ${theme.headerBg ? `rgba(0,0,0,0.06)` : `rgba(255,255,255,0.22)`};
        border-radius: 50%;
        display: flex; align-items: center; justify-content: center;
        font-size: 20px;
        flex-shrink: 0;
        border: 2px solid ${theme.headerBg ? `#D1AC88` : `rgba(255,255,255,0.35)`};
      }
      .chat-header-info { display: flex; flex-direction: column; gap: 2px; }
      .chat-header-logo {
        height: 28px; width: auto; object-fit: contain;
      }
      .chat-header-logo.max-concept {
        /* No filter — MAX logo shows in natural dark colors on white header */
        filter: none;
      }
      .chat-header-title {
        font-size: 14px; font-weight: 700; letter-spacing: 0.2px;
      }
      .chat-header-status {
        display: flex; align-items: center; gap: 5px;
        font-size: 11px; opacity: 0.9;
      }
      .chat-status-dot {
        width: 7px; height: 7px;
        background: #4ade80;
        border-radius: 50%;
        animation: statusPulse 2s infinite;
      }
      @keyframes statusPulse {
        0%,100% { opacity: 1; } 50% { opacity: 0.5; }
      }
      .chat-header-actions { display: flex; align-items: center; gap: 4px; }
      #close-chat {
        width: 32px; height: 32px;
        background: ${theme.headerBg ? `rgba(0,0,0,0.08)` : `rgba(255,255,255,0.18)`};
        color: ${theme.headerText || "white"};
        border-radius: 50%;
        display: flex; align-items: center; justify-content: center;
        cursor: pointer; font-size: 16px;
        transition: background 0.2s;
        flex-shrink: 0;
      }
      #close-chat:hover { background: rgba(255,255,255,0.32); }

      /* ── Chat Body ──────────────────────────────────────────────────────── */
      #chat-body {
        flex: 1;
        overflow-y: auto;
        overflow-x: hidden;
        padding: 16px 14px;
        display: flex;
        flex-direction: column;
        gap: 10px;
        background: #f5f7fa;
        -webkit-overflow-scrolling: touch;
      }
      #chat-body::-webkit-scrollbar { width: 4px; }
      #chat-body::-webkit-scrollbar-track { background: transparent; }
      #chat-body::-webkit-scrollbar-thumb { background: ${theme.primary}55; border-radius: 2px; }

      /* ── Message Bubbles ────────────────────────────────────────────────── */
      .bubble {
        display: flex;
        flex-direction: column;
        animation: bubbleIn 0.25s ease-out;
        word-wrap: break-word;
        overflow-wrap: break-word;
        max-width: 82%;
      }
      @keyframes bubbleIn {
        from { opacity: 0; transform: translateY(10px) scale(0.97); }
        to   { opacity: 1; transform: translateY(0) scale(1); }
      }

      /* Bot bubble — white card with left tail */
      .bot-bubble {
        align-self: flex-start;
        background: white;
        color: #1a1a1a;
        border-radius: 4px 16px 16px 16px;
        padding: 11px 14px;
        font-size: 14px;
        line-height: 1.55;
        box-shadow: 0 2px 8px rgba(0,0,0,0.07);
        border: 1px solid rgba(0,0,0,0.06);
        position: relative;
      }

      /* User bubble — brand gradient with right tail */
      .user-bubble {
        align-self: flex-end;
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        color: white;
        border-radius: 16px 4px 16px 16px;
        padding: 11px 14px;
        font-size: 14px;
        line-height: 1.55;
        box-shadow: 0 2px 10px rgba(0,0,0,0.12);
      }

      /* ── Typing Indicator ───────────────────────────────────────────────── */
      .typing-indicator {
        align-self: flex-start;
        display: flex; align-items: center; gap: 5px;
        background: white;
        border-radius: 4px 16px 16px 16px;
        padding: 12px 16px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.07);
        border: 1px solid rgba(0,0,0,0.06);
      }
      .typing-dot {
        width: 7px; height: 7px;
        background: ${theme.primary};
        border-radius: 50%;
        animation: typingBounce 1.2s infinite;
      }
      .typing-dot:nth-child(2) { animation-delay: 0.2s; }
      .typing-dot:nth-child(3) { animation-delay: 0.4s; }
      @keyframes typingBounce {
        0%,60%,100% { transform: translateY(0); opacity: 0.5; }
        30%          { transform: translateY(-6px); opacity: 1; }
      }

      /* ── Input Container ────────────────────────────────────────────────── */
      #chat-input-container {
        display: flex;
        align-items: center;
        padding: 10px 12px 10px;
        gap: 8px;
        background: white;
        border-top: 1px solid #edf0f4;
        flex-shrink: 0;
      }
      #chat-input {
        flex: 1;
        padding: 11px 16px;
        border: 1.5px solid #e5e7eb;
        border-radius: 24px;
        font-size: 14px;
        font-family: inherit;
        outline: none;
        background: #f9fafb;
        transition: border-color 0.2s, box-shadow 0.2s;
        min-height: 44px;
        color: #1a1a1a;
      }
      #chat-input:focus {
        border-color: ${theme.primary};
        background: white;
        box-shadow: 0 0 0 3px rgba(${hexToRgb(theme.primary)}, 0.12);
      }
      #chat-input::placeholder { color: #9ca3af; }

      #chat-send {
        width: 44px; height: 44px;
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        color: white;
        border: none;
        border-radius: 50%;
        cursor: pointer;
        display: flex; align-items: center; justify-content: center;
        font-size: 18px;
        flex-shrink: 0;
        transition: all 0.2s;
        box-shadow: 0 2px 8px rgba(0,0,0,0.15);
      }
      #chat-send:hover  { transform: scale(1.08); box-shadow: 0 4px 14px rgba(0,0,0,0.2); }
      #chat-send:active { transform: scale(0.94); }
      #chat-send:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
      #chat-input:disabled { opacity: 0.6; cursor: not-allowed; }

      /* ── Footer ─────────────────────────────────────────────────────────── */
      #chat-footer {
        text-align: center;
        font-size: 11px;
        padding: 6px;
        background: white;
        color: #9ca3af;
        border-top: 1px solid #f0f0f0;
        letter-spacing: 0.2px;
        flex-shrink: 0;
      }

      /* ── Loader Overlay ─────────────────────────────────────────────────── */
      .chat-loader {
        position: absolute;
        inset: 0;
        display: none;
        align-items: center;
        justify-content: center;
        background: rgba(255,255,255,0.88);
        z-index: 9998;
        backdrop-filter: blur(3px);
      }
      .chat-loader.active { display: flex; }
      .chat-loader-inner { display: flex; flex-direction: column; align-items: center; gap: 14px; }
      .chat-spinner {
        width: 36px; height: 36px;
        border: 3px solid rgba(0,0,0,0.06);
        border-top-color: ${theme.primary};
        border-radius: 50%;
        animation: spin 0.75s linear infinite;
      }
      @keyframes spin { to { transform: rotate(360deg); } }
      .chat-loader-text { font-size: 13px; color: #555; font-weight: 500; }

      /* ── Menu Buttons ───────────────────────────────────────────────────── */
      .menu-btn, #back-to-menu-btn {
        width: 100%;
        padding: 13px 16px;
        margin: 4px 0;
        border: 1.5px solid ${theme.primary}44;
        border-radius: 12px;
        background: white;
        color: #1a1a1a;
        cursor: pointer;
        text-align: left;
        font-weight: 600;
        font-size: 14px;
        transition: all 0.18s;
        font-family: inherit;
        display: flex; align-items: center; gap: 10px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.05);
        min-height: 48px;
      }
      .menu-btn:hover, #back-to-menu-btn:hover {
        background: linear-gradient(135deg, ${theme.primary}12, ${theme.secondary}08);
        border-color: ${theme.primary};
        transform: translateX(3px);
        box-shadow: 0 3px 12px rgba(0,0,0,0.08);
      }
      .menu-btn:active, #back-to-menu-btn:active { transform: translateX(0) scale(0.99); }

      .submenu-btn {
        width: 100%;
        padding: 13px 16px;
        margin: 4px 0;
        border: 1.5px solid ${theme.primary}55;
        border-radius: 12px;
        background: linear-gradient(135deg, ${theme.primary}10, ${theme.secondary}06);
        color: #1a1a1a;
        cursor: pointer;
        text-align: left;
        font-weight: 600;
        font-size: 14px;
        transition: all 0.18s;
        font-family: inherit;
        display: flex; align-items: center; gap: 10px;
        min-height: 48px;
      }
      .submenu-btn:hover {
        background: linear-gradient(135deg, ${theme.primary}22, ${theme.secondary}14);
        border-color: ${theme.primary};
        transform: translateX(3px);
      }
      .submenu-btn:active { transform: scale(0.99); }
      .submenu-btn.dynamic { border-style: dashed; }

      /* ── Order Card ─────────────────────────────────────────────────────── */
      .order-card {
        background: white;
        border: 1px solid ${theme.primary}33;
        border-top: 3px solid ${theme.primary};
        border-radius: 14px;
        padding: 14px;
        margin-top: 8px;
        box-shadow: 0 3px 12px rgba(0,0,0,0.06);
        display: flex;
        gap: 12px;
      }
      .order-card-image {
        width: 76px; height: 76px;
        border-radius: 10px;
        object-fit: cover;
        border: 1px solid #e5e7eb;
        flex-shrink: 0;
        background: #f3f4f6;
      }
      .order-card-content { flex: 1; min-width: 0; }
      .order-card-header {
        display: flex; align-items: flex-start;
        justify-content: space-between; gap: 6px; margin-bottom: 5px;
      }
      .order-card-title { font-weight: 700; color: #1a1a1a; font-size: 13px; }
      .order-status-badge {
        display: inline-block;
        padding: 3px 8px;
        font-weight: 700;
        font-size: 10px;
        color: white;
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        border-radius: 20px;
        white-space: nowrap;
        letter-spacing: 0.2px;
      }
      .order-card-meta { font-size: 12px; color: #666; margin-bottom: 8px; line-height: 1.5; }
      .order-products-strip {
        display: flex; gap: 6px; flex-wrap: wrap;
        margin-top: 8px; margin-bottom: 4px;
      }
      .order-product-thumb {
        width: 44px; height: 44px;
        border-radius: 8px; object-fit: cover;
        border: 1px solid #e5e7eb; flex-shrink: 0;
        background: #f3f4f6;
      }
      .order-card-actions {
        display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px;
      }
      .order-btn {
        padding: 8px 12px;
        border: none; border-radius: 8px;
        cursor: pointer; font-size: 12px; font-weight: 700;
        transition: all 0.18s; font-family: inherit;
        min-height: 36px;
      }
      .order-btn-primary  { background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary}); color: white; }
      .order-btn-primary:hover  { opacity: 0.88; transform: translateY(-1px); }
      .order-btn-secondary { background: white; border: 1.5px solid ${theme.primary}; color: ${theme.primary}; }
      .order-btn-secondary:hover { background: ${theme.primary}12; }

      /* ── Profile Card ───────────────────────────────────────────────────── */
      .profile-card {
        background: white;
        border: 1px solid ${theme.primary}33;
        border-top: 3px solid ${theme.primary};
        border-radius: 14px;
        padding: 14px;
        margin-top: 8px;
        box-shadow: 0 3px 12px rgba(0,0,0,0.06);
      }
      .profile-field {
        display: flex; justify-content: space-between; align-items: flex-start;
        padding: 9px 0;
        border-bottom: 1px solid #f3f4f6;
        font-size: 13px; line-height: 1.5;
      }
      .profile-field:last-child { border-bottom: none; }
      .profile-label { font-weight: 700; color: #1a1a1a; min-width: 80px; flex-shrink: 0; }
      .profile-value { color: #555; text-align: right; flex: 1; margin-left: 12px; word-break: break-word; }

      /* ── Toast ──────────────────────────────────────────────────────────── */
      #chat-copy-toast {
        position: fixed;
        bottom: 170px; right: 32px;
        background: #1a1a1a;
        color: white;
        padding: 10px 16px;
        border-radius: 10px;
        z-index: 10001;
        font-size: 13px; font-weight: 500;
        box-shadow: 0 6px 20px rgba(0,0,0,0.22);
        animation: toastIn 0.25s ease-out;
      }
      @keyframes toastIn {
        from { opacity: 0; transform: translateY(8px); }
        to   { opacity: 1; transform: translateY(0); }
      }

      /* ── Gift Card Inputs ───────────────────────────────────────────────── */
      .gift-card-input-container { display: flex; align-items: center; margin-top: 10px; gap: 8px; }
      .gift-card-input {
        flex: 1; padding: 11px 14px;
        border: 1.5px solid ${theme.primary};
        border-radius: 10px; font-size: 14px;
        outline: none; font-family: inherit;
        min-height: 44px;
      }
      .gift-card-input:focus { box-shadow: 0 0 0 3px rgba(${hexToRgb(theme.primary)}, 0.12); }
      .gift-card-btn {
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        color: white; border: none; border-radius: 10px;
        padding: 11px 16px; cursor: pointer;
        font-weight: 700; font-size: 13px;
        transition: all 0.2s; font-family: inherit;
        min-height: 44px;
      }
      .gift-card-btn:hover { opacity: 0.88; }

      /* ── Write to Us Form ───────────────────────────────────────────────── */
      .write-us-form {
        background: white;
        border: 1px solid #e5e7eb;
        border-top: 3px solid ${theme.primary};
        border-radius: 14px;
        padding: 16px;
        margin: 8px 0;
        display: flex; flex-direction: column; gap: 10px;
        box-shadow: 0 3px 12px rgba(0,0,0,0.06);
      }
      .write-us-form h4 { margin: 0 0 2px; font-size: 14px; color: ${theme.primary}; font-weight: 700; }
      .write-us-form p  { margin: 0 0 4px; font-size: 12px; color: #888; }
      .write-us-field { display: flex; flex-direction: column; gap: 4px; }
      .write-us-label { font-size: 12px; font-weight: 700; color: #555; }
      .write-us-input, .write-us-select, .write-us-textarea {
        width: 100%;
        padding: 10px 12px;
        border: 1.5px solid #e5e7eb;
        border-radius: 10px;
        font-size: 13px; font-family: inherit;
        outline: none;
        transition: border-color 0.2s;
        min-height: 44px;
      }
      .write-us-input:focus, .write-us-select:focus, .write-us-textarea:focus {
        border-color: ${theme.primary};
        box-shadow: 0 0 0 3px rgba(${hexToRgb(theme.primary)}, 0.1);
      }
      .write-us-textarea { resize: vertical; min-height: 80px; }
      .write-us-submit {
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        color: white; border: none; border-radius: 10px;
        padding: 12px; font-size: 14px; font-weight: 700;
        cursor: pointer; font-family: inherit;
        transition: opacity 0.2s;
        min-height: 48px;
      }
      .write-us-submit:hover    { opacity: 0.88; }
      .write-us-submit:disabled { opacity: 0.55; cursor: not-allowed; }

      /* ── Login Gate Card ────────────────────────────────────────────────── */
      .login-gate {
        background: white;
        border: 1px solid ${theme.primary}33;
        border-top: 3px solid ${theme.primary};
        border-radius: 16px;
        padding: 22px 18px 18px;
        margin: 6px 0;
        text-align: center;
        box-shadow: 0 4px 18px rgba(0,0,0,0.08);
        animation: bubbleIn 0.3s ease;
      }
      .login-gate-lock { font-size: 40px; line-height: 1; margin-bottom: 10px; }
      .login-gate-title { font-size: 15px; font-weight: 700; color: #1a1a1a; margin: 0 0 6px; }
      .login-gate-sub   { font-size: 12px; color: #666; margin: 0 0 14px; line-height: 1.5; }
      .login-gate-perks { list-style: none; padding: 0; margin: 0 0 16px; text-align: left; }
      .login-gate-perks li { font-size: 12px; color: #444; padding: 3px 0; }
      .login-gate-perks li::before { content: "✅ "; }
      .login-gate-btn {
        display: inline-block; width: 100%;
        padding: 13px 0; border: none; border-radius: 12px;
        font-size: 15px; font-weight: 700; cursor: pointer;
        letter-spacing: 0.3px;
        transition: opacity 0.2s, transform 0.1s;
        min-height: 48px;
      }
      .login-gate-btn:hover  { opacity: 0.88; transform: translateY(-1px); }
      .login-gate-btn:active { transform: translateY(0); }
      .login-gate-or { font-size: 11px; color: #bbb; margin: 10px 0 0; }

      /* ── Responsive fine-tuning ─────────────────────────────────────────── */
      @media (max-width: 768px) {
        .bubble { max-width: 88%; }
        .bot-bubble, .user-bubble { font-size: 14px; padding: 10px 13px; }
        .order-card { flex-direction: column; }
        .order-card-image { width: 100%; height: 140px; }
        .profile-field { flex-direction: column; }
        .profile-value { text-align: left; margin-left: 0; margin-top: 2px; }
        #chat-copy-toast { bottom: 110px; right: 16px; left: 16px; }
        .menu-btn, .submenu-btn, #back-to-menu-btn { font-size: 14px; padding: 14px 16px; }
      }
      @media (max-width: 360px) {
        .bot-bubble, .user-bubble { font-size: 13px; }
        .chat-header-title { font-size: 13px; }
      }
    `
    document.head.appendChild(style)
  }


  function hexToRgb(hex) {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
    return result
      ? `${Number.parseInt(result[1], 16)}, ${Number.parseInt(result[2], 16)}, ${Number.parseInt(result[3], 16)}`
      : "0, 0, 0"
  }

  function initChatWidget() {
    injectStyles()

    const chatWindow = createChatWindow()
    const chatBody = chatWindow.querySelector("#chat-body")
    const inputContainer = chatWindow.querySelector("#chat-input-container")
    const inputField = chatWindow.querySelector("#chat-input")
    const sendButton = chatWindow.querySelector("#chat-send")
    const loader = chatWindow.querySelector(".chat-loader")

    // ── Global free-text send (wired ONCE via addEventListener — never duplicates) ──
    // Each submenu that needs its own handler assigns sendButton.onclick (overrides this)
    // and clears it back to null when done. This global handler fires ONLY when
    // sendButton.onclick is null (i.e. no submenu has taken over the button).
    function globalSendHandler() {
      if (sendButton.onclick) return   // a submenu has registered its own handler
      const msg = inputField.value.trim()
      if (!msg) return
      renderUserMessage(msg)
      inputField.value = ""
      inputField.disabled = true
      sendButton.disabled = true
      inputField.placeholder = "Please wait..."
      handleFreeTextSend(msg)
    }
    sendButton.addEventListener("click", globalSendHandler)
    inputField.addEventListener("keydown", (e) => {
      if (e.key === "Enter") globalSendHandler()
    })

    function showLoader(message = "Please wait...") {
      const loaderText = loader.querySelector(".chat-loader-text")
      if (loaderText) loaderText.textContent = message
      loader.classList.add("active")
    }

    function hideLoader() {
      loader.classList.remove("active")
    }

    /** Re-enable the chat input bar after any response so the user can type again */
    function enableInput(placeholder) {
      inputField.disabled = false
      sendButton.disabled = false
      inputField.placeholder = placeholder || "Type your message..."
      inputField.focus()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API Layer
    // All backend communication is centralised here. Call-sites use named
    // methods (api.chat, api.getMenus, etc.) and receive { data, error }
    // so they never need to write try/catch themselves.
    //
    // The object lives inside initChatWidget so it can call showLoader /
    // hideLoader, which reference DOM nodes created at widget init time.
    // ─────────────────────────────────────────────────────────────────────────
    const api = {
      /**
       * Returns the standard request context shared by every endpoint.
       * @returns {{ userId: string, concept: string, env: string, appid: string }}
       */
      _context() {
        return {
          userId:      session.customerId,
          accessToken: session.accessToken,
          concept:     config.concept,
          env:         config.env,
          appid:       config.appid,
        }
      },

      /**
       * Authenticated GET helper.
       * @param {string} path  - Path appended to config.backend (e.g. "/menus")
       * @param {string} [loaderMsg] - Optional loader overlay text; omit for silent requests
       * @returns {Promise<{ data: any, error: string|null }>}
       */
      async _get(path, loaderMsg) {
        if (loaderMsg) showLoader(loaderMsg)
        try {
          const res = await fetch(`${config.backend}${path}`, {
            headers: { "X-API-Key": config.apikey },
          })
          if (loaderMsg) hideLoader()
          if (!res.ok) throw new Error(`HTTP ${res.status}`)
          return { data: await res.json(), error: null }
        } catch (err) {
          if (loaderMsg) hideLoader()
          console.error(`❌ API [GET ${path}]:`, err)
          return { data: null, error: err.message }
        }
      },

      /**
       * Authenticated POST helper.
       * @param {string} path   - Path appended to config.backend (e.g. "/chat")
       * @param {object} body   - JSON request body
       * @param {string} [loaderMsg] - Optional loader overlay text; omit for silent requests
       * @returns {Promise<{ data: any, error: string|null }>}
       */
      async _post(path, body, loaderMsg) {
        if (loaderMsg) showLoader(loaderMsg)
        try {
          const res = await fetch(`${config.backend}${path}`, {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-API-Key": config.apikey },
            body: JSON.stringify(body),
          })
          if (loaderMsg) hideLoader()
          if (!res.ok) throw new Error(`HTTP ${res.status}`)
          return { data: await res.json(), error: null }
        } catch (err) {
          if (loaderMsg) hideLoader()
          console.error(`❌ API [POST ${path}]:`, err)
          return { data: null, error: err.message }
        }
      },

      /**
       * Fetch top-level menu items with inline sub-menus for the current concept.
       * Single call returns the full menu tree — no second round-trip needed.
       */
      getMenus() {
        return this._get(`/menus?concept=${encodeURIComponent(config.concept)}`, "Loading menu...")
      },

      /**
       * Silently fetch the logged-in customer's profile directly from the
       * dedicated profile endpoint (POST /api/user/profile).
       *
       * Returns { data: UserWsDTO | null, error: string | null }.
       * Skips the call and returns { data: null, error: null } when there is
       * no session token (anonymous / not-logged-in user).
       */
      getProfile() {
        // Anonymous users have no accessToken — skip the round-trip entirely.
        if (!session.accessToken) return Promise.resolve({ data: null, error: null })

        // Derive base: "http://localhost:8080/api/chat" → "http://localhost:8080"
        const backendBase = config.backend.replace(/\/api\/chat.*$/, "")
        const url = `${backendBase}/api/user/profile`

        return (async () => {
          try {
            const res = await fetch(url, {
              method:  "POST",
              headers: { "Content-Type": "application/json", "X-API-Key": config.apikey },
              body: JSON.stringify({
                token:   session.accessToken,   // user's personal access_token
                concept: config.concept,
                env:     config.env,
                appId:   config.appid,
              }),
            })
            if (!res.ok) throw new Error(`HTTP ${res.status}`)
            // Response is UserWsDTO directly: { uid, firstName, lastName, email, ... }
            return { data: await res.json(), error: null }
          } catch (err) {
            console.warn("⚠️ Profile fetch failed:", err.message)
            return { data: null, error: err.message }
          }
        })()
      },

      /**
       * Send a user message to the chat endpoint.
       * @param {string} message - The user's text
       * @param {{ static?: boolean, [key: string]: any }} [extra] - Extra fields merged into the body.
       *   Pass { static: true } to route to /chat/ask instead of /chat.
       */
      chat(message, extra = {}) {
        const { static: isStatic, ...extraBody } = extra
        return this._post(
          isStatic ? "/chat/ask" : "/chat",
          { ...this._context(), message, question: message, ...extraBody },
          "Thinking...",
        )
      },

      /** Fetch the user's order list (TRACK_ORDER intent — existing). */
      trackOrders() {
        return this._post(
          "/chat",
          { ...this._context(), message: "order track", question: "order track" },
          "Checking your orders...",
        )
      },

      /** Fetch the user's order history (ORDER_LISTING intent — chatbot API). */
      getOrderList() {
        return this._post(
          "/chat",
          { ...this._context(), message: "show my order history", question: "show my order history" },
          "Fetching your orders...",
        )
      },

      /** Fetch the user's wallet / store-credit balance (WALLET_BALANCE intent). */
      getWalletBalance() {
        return this._post(
          "/chat",
          { ...this._context(), message: "wallet balance", question: "wallet balance" },
          "Checking your wallet...",
        )
      },

      /**
       * Check the balance of a gift card.
       * Note: uses env "www" as required by the gift card service (not config.env).
       * @param {string} cardNumber
       */
      checkGiftCard(cardNumber) {
        return this._post(
          "/chat",
          {
            ...this._context(),
            env: config.giftcardEnv,
            cardNumber,
            message: "Check my gift card balance",
          },
          "Fetching balance...",
        )
      },

      /**
       * Find stores near a location or pincode.
       * @param {{ latitude?: number, longitude?: number, pincode?: string }} params
       */
      getNearbyStores(params) {
        return this._post(
          "/chat/nearby-stores",
          {
            concept:     config.concept,
            env:         config.env,
            appId:       config.appid,
            userId:      session.customerId,
            accessToken: session.accessToken,
            ...params,
          },
          "Finding stores...",
        )
      },

      /**
       * Submit a Write-to-Us support ticket.
       * Uses /api/support/ticket (separate from /api/chat).
       */
      async submitTicket(ticketData) {
        const backendBase = config.backend.replace(/\/api\/chat.*$/, "")
        showLoader("Sending your message…")
        try {
          const res = await fetch(`${backendBase}/api/support/ticket`, {
            method:  "POST",
            headers: { "Content-Type": "application/json", "X-API-Key": config.apikey },
            body: JSON.stringify({
              ...ticketData,
              concept: config.concept,
              userId:  session.customerId || null,
              appid:   config.appid,
              env:     config.env,
            }),
          })
          hideLoader()
          if (!res.ok) throw new Error(`HTTP ${res.status}`)
          const data = await res.json()
          return { data, error: null }
        } catch (e) {
          hideLoader()
          return { data: null, error: e.message }
        }
      },
    }
    // ─────────────────────────────────────────────────────────────────────────

    const _cache = { profile: null, menus: null }

    const clearBody = () => (chatBody.innerHTML = "")

    const renderBotMessage = (msg, id = null) => {
      const bubble = document.createElement("div");
      bubble.className = "bubble bot-bubble";
      bubble.innerHTML = msg.replace(/\n/g, "<br/>");
    
      if (!id) {
        id = "msg-" + Date.now() + "-" + Math.floor(Math.random() * 99999);
      }
      bubble.id = id;
    
      chatBody.appendChild(bubble);
      chatBody.scrollTop = chatBody.scrollHeight;
    
      return id; // return message id for updates
    };
    
    // Update any message bubble
    const updateBotMessage = (id, newMsg) => {
      const el = document.getElementById(id);
      if (el) {
        el.innerHTML = newMsg.replace(/\n/g, "<br/>");
      }
    };
    

    const renderUserMessage = (msg) => {
      const bubble = document.createElement("div")
      bubble.className = "bubble user-bubble"
      bubble.innerHTML = msg
      chatBody.appendChild(bubble)
      chatBody.scrollTop = chatBody.scrollHeight
    }

    const renderBackToMenu = () => {
      const existing = document.getElementById("back-to-menu-btn")
      if (existing) existing.remove()

      const backBtn = document.createElement("button")
      backBtn.id = "back-to-menu-btn"
      backBtn.textContent = "⬅️ Back to Main Menu"
      backBtn.onclick = () => showGreeting()

      const footer = chatWindow.querySelector("#chat-footer")
      if (footer && footer.parentNode) {
        footer.parentNode.insertBefore(backBtn, footer)
      } else {
        chatWindow.appendChild(backBtn)
      }
    }

    /**
     * Opens the bottom input bar for collecting an order number.
     * Used by INTENT_HANDLERS when the AI response signals "needs input"
     * (i.e. chatMessage is present but no real data was returned).
     *
     * @param {string} placeholder  — input field hint text
     * @param {function} onSubmit   — called with the trimmed message string
     */
    /**
     * Opens the bottom input bar to collect user input for a specific intent.
     * No client-side validation — backend decides if the input is valid and
     * responds accordingly (asks again, shows data, or shows an error).
     *
     * Widget responsibility: collect input + show it as user bubble + send to backend.
     * Backend responsibility: validate, extract params, call tool, decide what to render.
     */
    function openOrderInput(placeholder, onSubmit) {
      enableInput(placeholder)
      sendButton.onclick   = null
      inputField.onkeydown = null

      const doSend = () => {
        const msg = inputField.value.trim()
        if (!msg) return                         // only guard: must not be empty

        renderUserMessage(msg)
        inputField.value     = ""
        sendButton.onclick   = null
        inputField.onkeydown = null
        inputField.disabled  = true
        sendButton.disabled  = true
        inputField.placeholder = "Please wait..."
        onSubmit(msg)                            // hand off to backend
      }

      sendButton.onclick   = doSend
      inputField.onkeydown = (e) => { if (e.key === "Enter") doSend() }
    }

    const INTENT_HANDLERS = {
      POLICY_QUESTION:    handleGeneralIntent,
      GENERAL_QUERY:      handleGeneralIntent,
      ORDER_TRACKING:     handleOrderTracking,
      CUSTOMER_PROFILE:   handleCustomerProfile,
      ORDER_LISTING:      handleChatbotOrderList,
      DELIVERY_TRACKING:  handleDeliveryTrackingResponse,
      RETURN_STATUS:      handleReturnStatusResponse,
      WALLET_BALANCE:     handleWalletBalanceResponse,
      STORE_LOCATOR:      handleStoreLocatorResponse,
      GIFT_CARD_BALANCE:  handleGiftCardBalanceResponse,
      WRITE_US:           handleWriteUs,
      DEFAULT:            handleDefaultIntent,
    }

    function handleGeneralIntent(payload) {
      renderBotMessage(payload.chat_message || payload.data || "No information found.")
      enableInput("Ask another question...")
      renderBackToMenu()
    }

    function handleDefaultIntent(payload) {
      renderBotMessage(payload.chat_message || payload.data || "No response available.")
      enableInput("Ask another question...")
      renderBackToMenu()
    }

    /**
     * STORE_LOCATOR intent — renders store cards from AI-routed free-text query.
     * payload is already json.data (StoreList DTO), so wrap to match renderStoreCards expectation.
     */
    function handleStoreLocatorResponse(payload) {
      const chatMsg = payload?.chatMessage || payload?.chat_message
      if (chatMsg) {
        renderBotMessage(chatMsg)
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }
      // renderStoreCards expects { data: { stores: [...] } }
      if (!renderStoreCards({ data: payload })) {
        renderBotMessage("😔 No nearby stores found. Try using the Store Locator menu button.")
      }
      renderBackToMenu()
      enableInput("Type your message...")
    }

    /**
     * GIFT_CARD_BALANCE intent — from AI-routed free-text query.
     * If backend returned balance data → show it.
     * Otherwise → trigger the gift card input flow to collect card number.
     */
    function handleGiftCardBalanceResponse(payload) {
      const chatMsg = payload?.chatMessage || payload?.chat_message
      // If backend has balance data ready, show it
      if (payload?.amount?.formattedValue) {
        const status = payload.active ? "✅ Active" : "❌ Inactive"
        chatBody.innerHTML += `
          <div class="bubble bot-bubble">
            🎁 <b>Gift Card Balance</b><br>
            Balance: <b>${payload.amount.formattedValue}</b><br>
            Status: ${status}
            ${payload.expiryDate ? `<br>Expires: ${payload.expiryDate}` : ""}
          </div>`
        chatBody.scrollTop = chatBody.scrollHeight
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }
      // No balance data — redirect to gift card input flow (manages its own input state)
      if (chatMsg) renderBotMessage(chatMsg)
      handleGiftCardBalance()
    }

    /**
     * WRITE_US intent — renders the Write-to-Us support ticket form.
     * Triggered by:
     *   a) Backend WRITE_US intent (user typed "raise ticket", "write to us", etc.)
     *   b) PolicyIntentHandler escalation when RAG finds 0 docs.
     *   c) "Write to Us" menu button.
     */
    function handleWriteUs(payload) {
      const introMsg = payload?.data || payload?.chat_message
        || "I'll help you reach our support team. Fill in the form below — we'll get back to you within 24 hours. 😊"
      renderBotMessage(introMsg)

      const CATEGORIES = [
        "Query", "Return", "Delivery Issue", "Late Delivery",
        "Cancellation", "Refund", "Exchange", "Damaged Item", "Other"
      ]

      // Auto-fill from profile if logged in
      const profileName  = session.profile?.name  || session.profile?.firstName || ""
      const profileEmail = session.profile?.email || ""
      const profilePhone = session.profile?.phone || session.profile?.mobileNumber || ""

      const form = document.createElement("div")
      form.className = "write-us-form"
      form.innerHTML = `
        <h4>✉️ Write to Us</h4>
        <p>We'll respond within 24 hours</p>

        <div class="write-us-field">
          <label class="write-us-label">Category *</label>
          <select class="write-us-select" id="wu-category">
            <option value="">— Select category —</option>
            ${CATEGORIES.map(c => `<option value="${c}">${c}</option>`).join("")}
          </select>
        </div>

        <div class="write-us-field">
          <label class="write-us-label">Describe your issue *</label>
          <textarea class="write-us-textarea" id="wu-message"
            placeholder="Please describe your issue in detail..."></textarea>
        </div>

        <div class="write-us-field">
          <label class="write-us-label">Name *</label>
          <input class="write-us-input" id="wu-name" type="text"
            placeholder="Your name" value="${profileName}">
        </div>

        <div class="write-us-field">
          <label class="write-us-label">Email *</label>
          <input class="write-us-input" id="wu-email" type="email"
            placeholder="your@email.com" value="${profileEmail}">
        </div>

        <div class="write-us-field">
          <label class="write-us-label">Phone</label>
          <input class="write-us-input" id="wu-phone" type="tel"
            placeholder="Mobile number (optional)" value="${profilePhone}">
        </div>

        <button class="write-us-submit" id="wu-submit">Submit Ticket</button>
      `
      chatBody.appendChild(form)
      chatBody.scrollTop = chatBody.scrollHeight

      // Submit handler
      form.querySelector("#wu-submit").addEventListener("click", async () => {
        const category = form.querySelector("#wu-category").value.trim()
        const message  = form.querySelector("#wu-message").value.trim()
        const name     = form.querySelector("#wu-name").value.trim()
        const email    = form.querySelector("#wu-email").value.trim()
        const phone    = form.querySelector("#wu-phone").value.trim()

        if (!category) { renderBotMessage("⚠️ Please select a category."); return }
        if (!message)  { renderBotMessage("⚠️ Please describe your issue."); return }
        if (!name)     { renderBotMessage("⚠️ Please enter your name."); return }
        if (!email)    { renderBotMessage("⚠️ Please enter your email."); return }

        // Disable to prevent double-submit
        const btn = form.querySelector("#wu-submit")
        btn.disabled = true
        btn.textContent = "Sending…"

        const { data, error } = await api.submitTicket({ name, email, phone, category, message })

        // Remove form from chat
        form.remove()

        if (error || !data?.success) {
          renderBotMessage(data?.message || "😔 Unable to send right now. Please try again or call us.")
        } else {
          renderBotMessage(`✅ <b>Ticket raised!</b><br>Reference: <b>${data.ticketId}</b><br>We'll get back to you within 24 hours.`)
        }
        renderBackToMenu()
      })
    }


    function handleOrderTracking(payload) {
      if (checkAndTriggerLogin(payload, "Please login to check your order details.")) return

      // Backend explicitly signalled it needs an order number from the user
      if (payload.needsOrderNumber) {
        renderBotMessage(payload.chat_message || "Please share your order number so I can look it up.")
        openOrderInput("Enter your order number...", (msg) =>
          sendMessageWithIntent("ORDER_TRACKING", msg, { orderNo: msg })
        )
        return
      }

      if (payload.chat_message && payload.chat_message.trim() !== "") {
        renderBotMessage(payload.chat_message)
      } else {
        renderBotMessage("<b>🧾 Order Details:</b>")

        if (Array.isArray(payload.orderDetailsList) && payload.orderDetailsList.length > 0) {
          payload.orderDetailsList.forEach((o) => {
            chatBody.innerHTML += renderOrderCard(o)
          })
        } else {
          renderBotMessage("No recent orders found.")
        }
      }
      renderBackToMenu()
      enableInput("Type your message...")
    }

    function handleCustomerProfile(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to view your profile.")) return

      const chatMsg = (payload?.chat_message || payload?.chatMessage || "").trim()
      if (chatMsg !== "") {
        renderBotMessage(chatMsg)
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }

      // Prefer profile from intent response, fallback to pre-fetched session cache
      const p = payload?.customerProfile || session.profile
      if (!p) {
        renderBotMessage("Profile not available. Please try again.")
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }

      // Keep session cache in sync for subsequent submenu clicks (instant render)
      session.profile = p

      const name = [(p.firstName || ""), (p.lastName || "")].map(s => s.trim()).filter(Boolean).join(" ") || "—"
      const genderMap = { MALE: "Male", FEMALE: "Female", OTHER: "Other" }
      const gender = genderMap[(p.gender || "").toUpperCase()] || "—"

      const rows = [
        { label: "Name",   value: name },
        { label: "Email",  value: p.email        || "—" },
        { label: "Mobile", value: p.signInMobile || "—" },
        { label: "Gender", value: gender },
      ]

      const card = document.createElement("div")
      card.className = "profile-card"
      card.innerHTML = rows.map((r, i) => `
        <div class="profile-field"${i === rows.length - 1 ? ' style="border-bottom:none"' : ''}>
          <span class="profile-label">${r.label}</span>
          <span class="profile-value">${r.value}</span>
        </div>`).join("")

      chatBody.appendChild(card)
      chatBody.scrollTop = chatBody.scrollHeight
      renderBackToMenu()
      enableInput("Type your message...")
    }

    /** Returns true when a real customer is logged in (not anonymous). */
    function isLoggedIn() {
      return session.customerId && session.customerId !== "anonymous"
    }

    /**
     * Renders a beautiful login-gate card inside the chat body.
     *
     * @param {string} context  - Short phrase describing what requires login,
     *                            e.g. "track your orders", "view wallet balance"
     */
    function renderLoginGate(context = "access your account") {
      const PERKS = [
        "Track orders & live delivery status",
        "View full order history",
        "Check wallet & gift card balance",
        "Manage returns & refunds instantly",
      ]

      const card = document.createElement("div")
      card.className = "login-gate"
      card.innerHTML = `
        <div class="login-gate-lock">🔐</div>
        <p class="login-gate-title">Sign in to ${context}</p>
        <p class="login-gate-sub">
          Your personal dashboard is just one tap away.<br>
          Log in to unlock everything.
        </p>
        <ul class="login-gate-perks">
          ${PERKS.map(p => `<li>${p}</li>`).join("")}
        </ul>
        <button class="login-gate-btn" id="gate-login-btn"
          style="background:${theme.primary}; color:#fff;">
          🚀 Login / Sign Up
        </button>
        <p class="login-gate-or">It only takes a few seconds ✨</p>
      `
      chatBody.appendChild(card)
      chatBody.scrollTop = chatBody.scrollHeight

      card.querySelector("#gate-login-btn").addEventListener("click", () => {
        // Collapse widget first so login modal opens on a clean screen
        chatContainer.classList.remove("open")

        setTimeout(() => {
          // Priority 1: host page's native signup button (production sites)
          const signupBtn = document.getElementById("account-actions-signup")
          if (signupBtn) {
            signupBtn.click()
            return
          }

          // Priority 2: login-modal-overlay (test / dev pages like index.html)
          const loginOverlay = document.getElementById("login-modal-overlay")
          if (loginOverlay) {
            loginOverlay.classList.add("open")
            const emailInput = document.getElementById("login-email")
            if (emailInput) setTimeout(() => emailInput.focus(), 100)
            return
          }

          // Priority 3: fire custom event — host page can listen and handle freely
          window.dispatchEvent(new CustomEvent("chatbot:login-requested"))
        }, 150)
      })

      renderBackToMenu()
    }

    function checkAndTriggerLogin(payload, defaultMsg = "Please login to continue.") {
      const cht = payload?.data?.chat_message || payload?.chat_message || ""
      const normalizedMsg = cht.trim().toLowerCase()

      const isLoginPrompt =
        normalizedMsg.includes("login") ||
        normalizedMsg.includes("sign in") ||
        normalizedMsg.includes("signin") ||
        normalizedMsg.includes("anonymous user")

      if (isLoginPrompt) {
        renderBotMessage(`
          ${cht || defaultMsg}
          <br><br>
          <a href="#" id="chat-login-link" style="color:${theme.primary}; text-decoration:underline; cursor:pointer; font-weight:600;">
            🔐 Click here to Login
          </a>
        `)

        setTimeout(() => {
          const loginLink = document.getElementById("chat-login-link")
          if (loginLink) {
            loginLink.addEventListener("click", (e) => {
              e.preventDefault()
              const signupBtn = document.getElementById("account-actions-signup")
              if (signupBtn) {
                signupBtn.click()
                console.log("🔑 Login popup triggered from chat link")
              }
            })
          }
        }, 300)

        renderBackToMenu()
        return true
      }
      return false
    }

    function extractOrderNumber(orderNo) {
      if (!orderNo) return "N/A"
      try {
        const m = orderNo.match(/\/order\/([^/?#]+)/i)
        if (m && m[1]) return m[1]
        const m2 = orderNo.match(/(\d{5,})/)
        if (m2) return m2[1]
        return orderNo.split("?")[0].split("#")[0]
      } catch {
        return orderNo
      }
    }

    window.copyToClipboard = async (text) => {
      try {
        await navigator.clipboard.writeText(text)
        let toast = document.getElementById("chat-copy-toast")
        if (!toast) {
          toast = document.createElement("div")
          toast.id = "chat-copy-toast"
          document.body.appendChild(toast)
        }
        toast.textContent = "✅ Order number copied!"
        toast.style.display = "block"
        clearTimeout(toast._t)
        toast._t = setTimeout(() => (toast.style.display = "none"), 1600)
      } catch {
        alert("Copy failed. Please copy manually.")
      }
    }

    const renderOrderCard = (o) => {
      const orderNumber = extractOrderNumber(o.orderNo)
      const orderUrl =
        o.orderNo && o.orderNo.startsWith("http")
          ? o.orderNo
          : `${window.location.origin}/my-account/order/${orderNumber}`
      const returnMsg = o.returnAllow ? "✅ Return Available" : "🚫 No Return"
      const exchangeMsg = o.exchangeAllow ? "♻️ Exchange Available" : "🚫 No Exchange"
      const statusBadge = o.latestStatus ? `<span class="order-status-badge">${o.latestStatus}</span>` : ""

      return `
        <div class="order-card">
          <img src="${o.imageURL || "https://via.placeholder.com/80"}" alt="Product" class="order-card-image">
          <div class="order-card-content">
            <div class="order-card-header">
              <div class="order-card-title">${o.productName || "Product"}</div>
              ${statusBadge}
            </div>
            <div class="order-card-meta">${o.color || ""}${o.size ? " | " + o.size : ""}</div>
            <div class="order-card-meta"><strong>Qty:</strong> ${o.qty || 1} | <strong>Net:</strong> ${o.netAmount || "-"}</div>
            <div class="order-card-meta"><strong>Order #:</strong> ${orderNumber}</div>
            <div class="order-card-actions">
              <a href="${orderUrl}" target="_blank" style="text-decoration:none;">
                <button class="order-btn order-btn-primary">View Order</button>
              </a>
              <button class="order-btn order-btn-secondary" onclick="copyToClipboard('${orderNumber}')">Copy Order #</button>
            </div>
            ${o.orderAmount ? `<div class="order-card-meta"><strong>Amount:</strong> ₹${o.orderAmount}</div>` : ""}
            ${o.estmtDate ? `<div class="order-card-meta"><strong>ETA:</strong> ${o.estmtDate}</div>` : ""}
            <div class="order-card-meta">${returnMsg} | ${exchangeMsg}</div>
          </div>
        </div>`
    }

    async function sendMessage(type, userMessage) {
      const { data: json, error } = await api.chat(userMessage, { static: type === "static" })
      if (error || !json) {
        renderBotMessage("⚠️ Something went wrong. Please try again.")
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }
      console.log("🧠 Chatbot Response:", json)
      const intent = json.intent || json.data?.intent || "DEFAULT"
      const payload = typeof json.data === "string" ? { chat_message: json.data } : json.data || json
      const handler = INTENT_HANDLERS[intent] || INTENT_HANDLERS.DEFAULT
      handler(payload)
    }

    /**
     * Like sendMessage, but pins the intent on the backend via `intentHint` so the
     * backend skips AI classification entirely and goes straight to the correct handler.
     *
     * Use this as the onSubmit callback inside openOrderInput whenever the frontend
     * already knows which intent should handle the follow-up (e.g. the user just
     * entered an order number after ORDER_TRACKING or DELIVERY_TRACKING asked for it).
     *
     * @param {string} intentHint  - Exact intent string, e.g. "ORDER_TRACKING"
     * @param {string} userMessage - The text the user typed (shown in chat + sent as message)
     * @param {object} [extra]     - Extra fields merged into the request body (e.g. { orderNo })
     */
    async function sendMessageWithIntent(intentHint, userMessage, extra = {}) {
      const { data: json, error } = await api.chat(userMessage, { intentHint, ...extra })
      if (error || !json) {
        renderBotMessage("⚠️ Something went wrong. Please try again.")
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }
      console.log("🧠 Chatbot Response (pinned intent=" + intentHint + "):", json)
      // Prefer the intent echoed back by the backend; fall back to the hint we sent
      const intent = json.intent || json.data?.intent || intentHint || "DEFAULT"
      const payload = typeof json.data === "string" ? { chat_message: json.data } : json.data || json
      const handler = INTENT_HANDLERS[intent] || INTENT_HANDLERS.DEFAULT
      handler(payload)
    }

    async function showGreeting() {
      clearBody()
      enableInput("Type your message...")

      let userName = null

      if (_cache.profile !== null && _cache.menus) {
        // Use cached data — no API calls, no loading animation
        userName = _cache.profile
        renderBotMessage(userName ? `👋 Hi &nbsp;<strong>${userName}</strong>!` : `👋 Hi!`)
      } else {
        // Show a typing-animated placeholder while the profile loads silently
        const loadingId = renderBotMessage("✨ Just a moment…")
        startTypingAnimation(loadingId)

        // api.getProfile() is called without a loaderMsg so the typing animation
        // acts as the UX indicator instead of the full-screen overlay.
        const { data: profileResult } = await api.getProfile()
        // profileResult is UserWsDTO: { firstName, lastName, name, uid, email, ... }
        userName =
          profileResult?.firstName ||
          profileResult?.name       ||
          null
        _cache.profile = userName

        stopTypingAnimation(loadingId)
        updateBotMessage(loadingId, userName ? `👋 Hi &nbsp;<strong>${userName}</strong>!` : `👋 Hi!`)
      }

      renderBotMessage(`Welcome to &nbsp;<strong>${config.concept}</strong> Chat Service.`)

      // Load and render top-level menus (use cache if available)
      if (_cache.menus) {
        _cache.menus.forEach((menu) => renderMenuButton(menu))
        renderBackToMenu()
        return
      }

      const { data: menus, error: menuError } = await api.getMenus()
      if (menuError || !menus) {
        renderBotMessage("⚠️ Unable to load menu right now.")
      } else {
        const sorted = menus
          .sort((a, b) => a.displayOrder - b.displayOrder)
          .map((menu) => {
            if (menu.subMenus?.length) {
              menu.subMenus.sort((a, b) => a.displayOrder - b.displayOrder)
            }
            return menu
          })
        _cache.menus = sorted
        sorted.forEach((menu) => renderMenuButton(menu))
      }

      renderBackToMenu()
    }
    
    let typingIntervals = {};

    function startTypingAnimation(id) {
      let dots = 0;
      typingIntervals[id] = setInterval(() => {
        dots = (dots + 1) % 4;
        updateBotMessage(id, `⏳ ⏳ One moment while I set things up…${".".repeat(dots)}`);
        chatBody.scrollTop = chatBody.scrollHeight;
      }, 450);
    }
    
    function stopTypingAnimation(id) {
      clearInterval(typingIntervals[id]);
      delete typingIntervals[id];
    }
    

    const renderMenuButton = (menu) => {
      const btn = document.createElement("button")
      btn.className = "menu-btn"
      btn.textContent = (menu.icon ? menu.icon + " " : "") + menu.title
      btn.onclick = () => showSubMenus(menu)
      chatBody.appendChild(btn)
    }

    function showSubMenus(menu) {
      clearBody()
      renderUserMessage((menu.icon ? menu.icon + " " : "") + menu.title)
      // Sub-menus are already embedded inline — no extra API call needed
      const subs = menu.subMenus || []
      if (!subs.length) {
        renderBotMessage("⚠️ No options available for this menu.")
      } else {
        renderBotMessage(`Choose an option for <b>${menu.title}</b>:`)
        subs.forEach((sub) => renderSubmenuButton(sub))
      }
      renderBackToMenu()
    }

    const renderSubmenuButton = (sub) => {
      const sbtn = document.createElement("button")
      sbtn.className = "submenu-btn"
      sbtn.textContent = (sub.icon ? sub.icon + " " : "") + sub.title
      sbtn.onclick = () => handleSubmenu(sub)
      chatBody.appendChild(sbtn)
    }

    /**
     * Handles free-text input from the chat input box.
     * All messages go directly to the backend — no frontend interception.
     */
    function handleFreeTextSend(msg) {
      sendMessage(null, msg)
    }

    /**
     * intentKey dispatch map.
     *
     * Each entry maps an intentKey string to an async handler function.
     * The widget calls the matching handler, or falls back to OPEN_INPUT for
     * any key that is not listed here (including null / undefined keys).
     *
     * To add a new integration, just add a new key → async function entry here.
     */
    // ─── Auth-required intent keys ─────────────────────────────────────────
    // Any key listed here will show renderLoginGate() for anonymous users
    // instead of hitting the backend. Add new auth-protected intents here.
    const AUTH_REQUIRED_INTENTS = {
      TRACK_ORDER:      "track your orders",
      ORDER_LISTING:    "view your order history",
      DELIVERY_TRACKING:"track your deliveries",
      RETURN_STATUS:    "check your return & refund status",
      WALLET_BALANCE:   "view your wallet balance",
      CUSTOMER_PROFILE: "access your account & profile",
    }

    const SUBMENU_INTENT_HANDLERS = {
      // ── No auth required ──────────────────────────────────────────────────
      NEARBY_STORE:        async () => { await handleNearbyStore() },
      GIFT_CARD_BALANCE:   async () => { await handleGiftCardBalance() },
      // Write Us — opens the support ticket form directly (no backend call needed)
      WRITE_US:            () => { handleWriteUs({}) },

      // ── Auth-required (gate shows instantly for anonymous users) ──────────
      TRACK_ORDER:         async () => { await handleOrderTrackMenu() },
      ORDER_LISTING:       async () => { await handleOrderListingMenu() },
      DELIVERY_TRACKING:   async () => { await handleDeliveryTrackingMenu() },
      RETURN_STATUS:       async () => { await handleReturnStatusMenu() },
      WALLET_BALANCE:      async () => { await handleWalletBalanceMenu() },
      // My Profile — awaits pre-fetch then reads from session cache
      CUSTOMER_PROFILE:    async () => {
        if (profileCachePromise) await profileCachePromise
        handleCustomerProfile({ customerProfile: session.profile })
      },
    }

    async function handleSubmenu(sub) {
      clearBody()
      renderUserMessage((sub.icon ? sub.icon + " " : "") + sub.title)

      const key     = (sub.intentKey || "").trim().toUpperCase()

      // ── Auth gate: show login card immediately for anonymous users ────────
      if (!isLoggedIn() && AUTH_REQUIRED_INTENTS[key]) {
        renderLoginGate(AUTH_REQUIRED_INTENTS[key])
        return
      }

      const handler = SUBMENU_INTENT_HANDLERS[key]

      if (handler) {
        await handler()
      } else {
        // Unknown intentKey → free-text input fallback with topic intercept
        renderBotMessage(`Please enter your question related to <b>${sub.title}</b>.`)
        enableInput(`Ask about ${sub.title}...`)

        // Reset before assigning to prevent duplicate handlers
        sendButton.onclick    = null
        inputField.onkeydown  = null

        const doSend = () => {
          const msg = inputField.value.trim()
          if (!msg) return
          renderUserMessage(msg)
          inputField.value     = ""
          sendButton.onclick   = null
          inputField.onkeydown = null
          inputField.disabled  = true
          sendButton.disabled  = true
          inputField.placeholder = "Please wait..."
          handleFreeTextSend(msg)
        }
        sendButton.onclick   = doSend
        inputField.onkeydown = (e) => { if (e.key === "Enter") doSend() }
      }
      renderBackToMenu()
    }


    /**
     * ORDER_TRACKING menu entry-point.
     * Prompts the user to enter a numeric order number, validates it,
     * then sends it to the backend as message + orderNo field.
     */
    async function handleOrderTrackMenu() {
      if (!session.customerId) {
        renderBotMessage("🔒 Please sign in first to track your orders.")
        renderBackToMenu()
        return
      }

      renderBotMessage("📦 Please enter your <b>Order Number</b> to check its status.")

      openOrderInput("Enter your order number...", async (raw) => {
        showLoader("Looking up your order…")
        const { data: json, error } = await api._post(
          "/chat",
          { ...api._context(), message: "Track order " + raw, orderNo: raw },
          null
        )
        hideLoader()

        if (error || !json) {
          renderBotMessage("⚠️ Unable to fetch order details. Please try again later.")
          renderBackToMenu()
          return
        }

        const payload = typeof json.data === "string" ? { chat_message: json.data } : json.data || json
        handleOrderTracking(payload)
      })
    }
    

    async function handleGiftCardBalance() {
      renderBotMessage("🎁 Please enter your gift card number below to check your balance:")

      // Note: renamed from inputContainer to giftCardContainer to avoid
      // shadowing the outer inputContainer (the chat text-input bar).
      const giftCardContainer = document.createElement("div")
      giftCardContainer.className = "gift-card-input-container"

      const input = document.createElement("input")
      input.type = "text"
      input.className = "gift-card-input"
      input.placeholder = "Enter 16-digit Gift Card Number"
      input.maxLength = 16
      input.pattern = "[0-9]*"

      const button = document.createElement("button")
      button.className = "gift-card-btn"
      button.textContent = "Check Balance"

      giftCardContainer.appendChild(input)
      giftCardContainer.appendChild(button)
      chatBody.appendChild(giftCardContainer)
      chatBody.scrollTop = chatBody.scrollHeight

      button.onclick = async () => {
        const cardNumber = input.value.trim()

        if (!cardNumber || !/^[0-9]{6,19}$/.test(cardNumber)) {
          renderBotMessage("⚠️ Please enter a valid gift card number (numbers only).")
          return
        }

        renderUserMessage(`🔢 Gift Card: ${cardNumber}`)
        renderBotMessage("💳 Checking your gift card balance...")

        const { data: json, error } = await api.checkGiftCard(cardNumber)

        if (error || !json) {
          renderBotMessage("⚠️ Something went wrong while checking your gift card balance.")
          renderBackToMenu()
          return
        }

        const data = json?.data || json

        if (data?.errorOccurred) {
          const errorReason = data?.errors?.[0]?.message || ""
          if (errorReason.includes("not.found")) {
            renderBotMessage("❌ Invalid gift card number. Please check and try again.")
          } else if (errorReason.includes("server.error")) {
            renderBotMessage("⚠️ Gift card service is currently unavailable. Please try later.")
          } else {
            renderBotMessage(data.chat_message || "😔 Unable to fetch your gift card balance.")
          }
        } else if (data?.amount != null) {
          const expiry = data.expiryDate ? data.expiryDate.split("T")[0] : "N/A"
          const rows = [
            { label: "Card Number", value: cardNumber || "N/A" },
            { label: "Balance",     value: data.amount?.formattedValue || "N/A" },
            { label: "Status",      value: data.active ? "Active" : "Inactive" },
            { label: "Valid Until", value: expiry },
          ]
          const card = document.createElement("div")
          card.className = "profile-card"
          card.innerHTML = rows.map((r, i) => `
            <div class="profile-field"${i === rows.length - 1 ? ' style="border-bottom:none"' : ''}>
              <span class="profile-label">${r.label}</span>
              <span class="profile-value">${r.value}</span>
            </div>`).join("")
          chatBody.appendChild(card)
        } else {
          renderBotMessage(data?.chat_message || "😔 Unable to fetch your gift card balance.")
        }

        renderBackToMenu()
        chatBody.scrollTop = chatBody.scrollHeight
      }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORDER LISTING  (submenu entry-point + INTENT_HANDLER response renderer)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called when user taps "📋 Order History" submenu button. */
    async function handleOrderListingMenu() {
      const { data: json, error } = await api.getOrderList()
      if (error || !json) {
        renderBotMessage("⚠️ Unable to fetch your order history. Please try again later.")
        renderBackToMenu()
        return
      }
      const payload = typeof json.data === "string" ? { chat_message: json.data } : json.data || json
      handleChatbotOrderList(payload)
    }

    /**
     * Renders the ChatbotOrderListResponse returned by the ORDER_LISTING intent.
     * Expected shape: { orders: [{ orderNo, orderStatus, orderDate, orderAmount, entries[] }], chatMessage }
     */
    function handleChatbotOrderList(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to view your order history.")) return

      if (payload.chatMessage && payload.chatMessage.trim() !== "") {
        renderBotMessage(payload.chatMessage)
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }

      // Accept both "orders" (chatbot endpoint) and "orderDataList" (standard Hybris API)
      const orders = Array.isArray(payload.orders)
        ? payload.orders
        : Array.isArray(payload.orderDataList)
          ? payload.orderDataList
          : []
      if (orders.length === 0) {
        renderBotMessage("📦 No orders found in your history.")
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }

      renderBotMessage("<b>📋 Your Order History:</b>")
      orders.forEach((o) => {
        // Support both simplified chatbot fields and standard Hybris field names
        const orderNo  = o.orderNo  || o.code  || "N/A"
        const status   = o.orderStatus || o.statusDisplay || o.status || "Unknown"
        const rawDate  = o.orderDate  || o.created
        const date     = rawDate ? new Date(rawDate).toLocaleDateString() : "N/A"
        const amount   = o.orderAmount != null
          ? `₹${o.orderAmount}`
          : (o.totalPrice?.value != null ? `₹${o.totalPrice.value}` : "N/A")
        const orderUrl = `${window.location.origin}/my-account/order/${orderNo}`

        // Build product thumbnails strip from entries[]
        // Resolves image from either:
        //   1. entry.productImage          (chatbot-simplified endpoint)
        //   2. entry.resolvedProductImage  (Java helper serialised by Spring)
        //   3. entry.product.images[0].url (standard Hybris order API)
        const entries = Array.isArray(o.entries) ? o.entries : []
        const resolveImg = (e) =>
          e.productImage
          || e.resolvedProductImage
          || e.product?.images?.[0]?.url
          || null
        const resolveUrl = (e) => {
          const rel = e.productUrl || e.resolvedProductUrl || e.product?.url
          return rel ? `${window.location.origin}${rel}` : orderUrl
        }

        const thumbEntries = entries.filter(e => resolveImg(e)).slice(0, 5)
        const thumbsHtml = thumbEntries.length > 0
          ? `<div class="order-products-strip">` +
            thumbEntries.map(e =>
              `<a href="${resolveUrl(e)}" target="_blank" title="View product">` +
                `<img src="${resolveImg(e)}" alt="Product" class="order-product-thumb" ` +
                     `onerror="this.style.display='none'">` +
              `</a>`
            ).join("") +
            `</div>`
          : ""

        chatBody.innerHTML += `
          <div class="order-card">
            <div class="order-card-content">
              <div class="order-card-header">
                <div class="order-card-title">Order #${orderNo}</div>
                <span class="order-status-badge">${status}</span>
              </div>
              ${thumbsHtml}
              <div class="order-card-meta"><strong>Date:</strong> ${date}</div>
              <div class="order-card-meta"><strong>Amount:</strong> ${amount}</div>
              <div class="order-card-actions">
                <a href="${orderUrl}" target="_blank" style="text-decoration:none;">
                  <button class="order-btn order-btn-primary">View Order</button>
                </a>
                <button class="order-btn order-btn-secondary" onclick="copyToClipboard('${orderNo}')">Copy #</button>
              </div>
            </div>
          </div>`
      })
      chatBody.scrollTop = chatBody.scrollHeight
      renderBackToMenu()
      enableInput("Type your message...")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELIVERY TRACKING  (submenu entry-point + INTENT_HANDLER response renderer)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called when user taps "🚚 Delivery Status" submenu button. */
    async function handleDeliveryTrackingMenu() {
      renderBotMessage("🚚 Please enter your <b>Order Number</b> to check your delivery status. Example: <i>LS12345678</i>")
      openOrderInput("Enter your order number...", (msg) =>
        sendMessageWithIntent("DELIVERY_TRACKING", msg, { orderNo: msg })
      )
    }

    /**
     * Renders the DeliveryResponse returned by the DELIVERY_TRACKING intent.
     * Expected shape: { orderNo, item_details: [{ position, quantity, status, consignment, returnDetail }], chatMessage }
     */
    function handleDeliveryTrackingResponse(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to check your delivery status.")) return

      // Backend returned a message but no delivery data → needs order number from user
      if (payload.chatMessage && payload.chatMessage.trim() !== "") {
        renderBotMessage(payload.chatMessage)
        openOrderInput("Enter your order number...", (msg) =>
          sendMessageWithIntent("DELIVERY_TRACKING", msg, { orderNo: msg })
        )
        return
      }

      const items = Array.isArray(payload.item_details) ? payload.item_details : []
      if (items.length === 0) {
        renderBotMessage("🚚 No delivery details found for that order.")
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }

      renderBotMessage(`<b>🚚 Delivery Status — Order #${payload.orderNo || "N/A"}:</b>`)
      items.forEach((item) => {
        const c      = item.consignment || {}
        const status = c.consignmentStatus || item.status || "Unknown"
        const shipped = c.shippedDate        ? `<div class="order-card-meta"><strong>Shipped:</strong> ${new Date(c.shippedDate).toLocaleDateString()}</div>` : ""
        const eta     = c.actualDeliveryDate  ? `<div class="order-card-meta"><strong>ETA:</strong> ${new Date(c.actualDeliveryDate).toLocaleDateString()}</div>` : ""
        const breached = c.deliveryTatBreached ? `<div class="order-card-meta" style="color:#e53935;">⚠️ Delivery SLA breached</div>` : ""
        chatBody.innerHTML += `
          <div class="order-card">
            <div class="order-card-content">
              <div class="order-card-header">
                <div class="order-card-title">Item ${item.position || ""} — Qty ${item.quantity || 1}</div>
                <span class="order-status-badge">${status}</span>
              </div>
              ${c.consignmentCode ? `<div class="order-card-meta"><strong>Tracking #:</strong> ${c.consignmentCode}</div>` : ""}
              ${shipped}${eta}${breached}
            </div>
          </div>`
      })
      chatBody.scrollTop = chatBody.scrollHeight
      renderBackToMenu()
      enableInput("Type your message...")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETURN STATUS  (submenu entry-point + INTENT_HANDLER response renderer)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called when user taps "↩️ Return / Refund" submenu button. */
    async function handleReturnStatusMenu() {
      renderBotMessage(
        "↩️ Please enter your <b>Order Number</b> to check your return / refund status. " +
        "Example: <i>LS12345678</i>"
      )
      openOrderInput("Enter your order number...", (msg) =>
        sendMessageWithIntent("RETURN_STATUS", msg, { orderNo: msg })
      )
    }

    /**
     * Renders the ReturnStatusResponse returned by the RETURN_STATUS intent.
     * Expected shape: { consignmentCode, returnId, rma, returnStatus, returnCreationDate,
     *                   totalRefundAmount, faqLink, chatMessage }
     */
    function handleReturnStatusResponse(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to check your return status.")) return

      // Backend returned a message but no return data → needs order number from user
      if (payload.chatMessage && payload.chatMessage.trim() !== "") {
        renderBotMessage(payload.chatMessage)
        openOrderInput("Enter your order number...", (msg) =>
          sendMessageWithIntent("RETURN_STATUS", msg, { orderNo: msg })
        )
        return
      }

      renderBotMessage("<b>↩️ Return / Refund Status:</b>")
      const returnDate = payload.returnCreationDate
        ? new Date(payload.returnCreationDate).toLocaleDateString()
        : "N/A"
      chatBody.innerHTML += `
        <div class="order-card">
          <div class="order-card-content">
            <div class="order-card-header">
              <div class="order-card-title">RMA #${payload.rma || "N/A"}</div>
              <span class="order-status-badge">${payload.returnStatus || "Unknown"}</span>
            </div>
            ${payload.consignmentCode ? `<div class="order-card-meta"><strong>Consignment:</strong> ${payload.consignmentCode}</div>` : ""}
            <div class="order-card-meta"><strong>Created:</strong> ${returnDate}</div>
            ${payload.totalRefundAmount != null ? `<div class="order-card-meta"><strong>Refund Amount:</strong> ₹${payload.totalRefundAmount}</div>` : ""}
            ${payload.faqLink ? `<div class="order-card-meta"><a href="${payload.faqLink}" target="_blank" style="color:${theme.primary};font-weight:600;">📖 Return FAQ</a></div>` : ""}
          </div>
        </div>`
      chatBody.scrollTop = chatBody.scrollHeight
      renderBackToMenu()
      enableInput("Type your message...")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WALLET BALANCE  (submenu entry-point + INTENT_HANDLER response renderer)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called when user taps "💰 Wallet Balance" submenu button. */
    async function handleWalletBalanceMenu() {
      const { data: json, error } = await api.getWalletBalance()
      if (error || !json) {
        renderBotMessage("⚠️ Unable to fetch your wallet balance. Please try again later.")
        renderBackToMenu()
        return
      }
      const payload = typeof json.data === "string" ? { chatMessage: json.data } : json.data || json
      handleWalletBalanceResponse(payload)
    }

    /**
     * Renders the WalletResponse returned by the WALLET_BALANCE intent.
     * Expected shape: { walletAmount, walletLink, chatMessage }
     */
    function handleWalletBalanceResponse(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to view your wallet balance.")) return

      if (payload.chatMessage && payload.chatMessage.trim() !== "") {
        renderBotMessage(payload.chatMessage)
        renderBackToMenu()
        enableInput("Type your message...")
        return
      }

      const amount  = payload.walletAmount != null ? `₹${payload.walletAmount.toFixed(2)}` : "N/A"
      const walletUrl = payload.walletLink
        ? `${window.location.origin}${payload.walletLink}`
        : null

      renderBotMessage("<b>💰 Your Wallet Balance:</b>")
      chatBody.innerHTML += `
        <div class="order-card">
          <div class="order-card-content">
            <div class="order-card-header">
              <div class="order-card-title" style="font-size:1.4rem;">${amount}</div>
            </div>
            <div class="order-card-meta" style="color:#555;">Available store credit / wallet balance</div>
            ${walletUrl ? `
            <div class="order-card-actions" style="margin-top:8px;">
              <a href="${walletUrl}" target="_blank" style="text-decoration:none;">
                <button class="order-btn order-btn-primary">💳 View Wallet Details</button>
              </a>
            </div>` : ""}
          </div>
        </div>`
      chatBody.scrollTop = chatBody.scrollHeight
      renderBackToMenu()
      enableInput("Type your message...")
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Render a list of store cards into the chat body.
     * Extracted to avoid duplicating the rendering logic across GPS and pincode flows.
     * @param {object} json - Raw API response from api.getNearbyStores
     * @returns {boolean} true if stores were rendered, false if none found
     */
    function renderStoreCards(json) {
      const stores = json?.data?.stores
      if (!stores?.length) return false
      stores.forEach((s) => {
        chatBody.innerHTML += `
          <div class="bubble bot-bubble" style="border:1px solid ${theme.primary};">
            <b>${s.storeName}</b><br/>
            ${s.line1 || ""} ${s.line2 ? "- " + s.line2 : ""} ${s.postalCode ? "- " + s.postalCode : ""}<br/>
            ${s.contactNumber ? "📞 " + s.contactNumber + "<br/>" : ""}
            ${s.workingHours ? "🕒 " + s.workingHours + "<br/>" : ""}
            <a href="https://www.google.com/maps?q=${s.latitude},${s.longitude}" target="_blank"
               style="color:${theme.primary};font-weight:600;text-decoration:none;">📍 View on Map</a>
          </div>`
      })
      return true
    }

    async function handleNearbyStore() {
      renderBotMessage("📍 Detecting your location...")
      if (!navigator.geolocation) {
        renderBotMessage("⚠️ Geolocation not supported.")
        showPincodeOption()
        return
      }
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          const { latitude: lat, longitude: lon } = pos.coords
          renderBotMessage(`✅ Found location (${lat.toFixed(4)}, ${lon.toFixed(4)})`)
          renderBotMessage("Fetching nearby stores...")
          const { data: json, error } = await api.getNearbyStores({ latitude: lat, longitude: lon })
          if (error || !renderStoreCards(json)) {
            renderBotMessage("😔 No nearby stores found.")
          }
          renderBackToMenu()
        },
        () => {
          renderBotMessage("❌ Permission denied for location.")
          showPincodeOption()
        },
      )
    }
    function showPincodeOption() {
      const pincodeContainer = document.createElement("div");
      pincodeContainer.id = "pincode-fallback";
      pincodeContainer.style.cssText = `
        display: flex;
        flex-direction: column;
        gap: 10px;
        margin: 16px 0;
        padding: 16px;
        border: 2px solid ${theme.primary};
        border-radius: 10px;
        background-color: #fff8e7;
        max-width: 100%;
      `;
    
      const label = document.createElement("div");
      label.style.cssText = `
        font-size: 15px;
        font-weight: 600;
        color: #333;
        line-height: 1.5;
      `;
      label.textContent = "No problem! Enter your PIN code to find nearby stores:";
    
      const inputWrapper = document.createElement("div");
      inputWrapper.style.cssText = `
        display: flex;
        flex-direction: column;
        gap: 10px;
        width: 100%;
        margin-top: 6px;
      `;
    
      const input = document.createElement("input");
      input.type = "text";
      input.id = "pincode-input";
      input.placeholder = "Enter 6-digit PIN";
      input.maxLength = 6;
      input.inputMode = "numeric";
      input.style.cssText = `
        width: 100%;
        padding: 10px 12px;
        border: 1px solid #ccc;
        border-radius: 6px;
        font-size: 15px;
        font-family: inherit;
        outline: none;
        transition: border-color 0.2s ease;
      `;
      input.addEventListener("focus", () => {
        input.style.borderColor = theme.primary;
      });
      input.addEventListener("blur", () => {
        input.style.borderColor = "#ccc";
      });
    
      const button = document.createElement("button");
      button.textContent = "Search";
      button.style.cssText = `
        width: 100%;
        padding: 10px 0;
        background-color: ${theme.primary};
        color: white;
        border: none;
        border-radius: 6px;
        font-size: 15px;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.2s ease;
      `;
      button.addEventListener("mouseover", () => (button.style.opacity = "0.9"));
      button.addEventListener("mouseout", () => (button.style.opacity = "1"));
    
      // Attach handlers
      button.addEventListener("click", () => handlePincodeSearch(input.value));
      input.addEventListener("keypress", (e) => {
        if (e.key === "Enter") handlePincodeSearch(input.value);
      });
    
      // Assemble layout
      inputWrapper.appendChild(input);
      inputWrapper.appendChild(button);
      pincodeContainer.appendChild(label);
      pincodeContainer.appendChild(inputWrapper);
      chatBody.appendChild(pincodeContainer);
    
      input.focus();
    }
    
    
    
    async function handlePincodeSearch(pincode) {
      if (!pincode || pincode.trim().length === 0) {
        renderBotMessage("⚠️ Please enter a valid pincode.")
        return
      }
      renderBotMessage(`🔍 Searching stores for pincode: ${pincode}`)
      const { data: json, error } = await api.getNearbyStores({ pincode: pincode.trim() })
      if (error || !renderStoreCards(json)) {
        renderBotMessage("😔 No nearby stores found.")
      }
      renderBackToMenu()
    }

    createFloatingButton(chatWindow, showGreeting)

  }

  function createFloatingButton(chatWindow, showGreeting) {
    const button = document.createElement("div")
    button.id = "chatbot-button"
    button.innerHTML = `
      <div class="chat-fab-icon">
        <img src="${theme.logo}" alt="${config.concept}" />
      </div>
      <div class="chat-fab-badge">💬</div>`

    document.body.appendChild(button)

    button.onclick = () => {
      chatWindow.classList.toggle("open")
      if (chatWindow.classList.contains("open")) {
        showGreeting()
      }
    }

    function adjustFabForViewport() {
      const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0)
      if (vw < 480) {
        button.style.bottom = "16px"
        button.style.right = "16px"
      } else {
        button.style.bottom = "24px"
        button.style.right = "24px"
      }
    }

    adjustFabForViewport()
    window.addEventListener("resize", adjustFabForViewport)
  }

  function createChatWindow() {
    const chatWindow = document.createElement("div")
    chatWindow.id = "chatbot-container"

    const isDarkHeader = ["MAX", "LIFESTYLE", "HOMECENTRE"].includes(config.concept)

    chatWindow.innerHTML = `
      <!-- Drag handle — visible only on mobile -->
      <div class="chat-drag-handle">
        <div class="chat-drag-handle-bar"></div>
      </div>

      <!-- Header -->
      <div class="chat-header">
        <div class="chat-header-left">
          <div class="chat-header-avatar">🤖</div>
          <div class="chat-header-info">
            <img
              src="${theme.logo}"
              alt="${config.concept} logo"
              class="chat-header-logo ${config.concept === 'MAX' || config.concept === 'HOMECENTRE' ? 'max-concept' : ''}"
            />
            <div class="chat-header-status">
              <span class="chat-status-dot"></span>
              <span>Online — ready to help</span>
            </div>
          </div>
        </div>
        <div class="chat-header-actions">
          <span id="close-chat" title="Close chat">✕</span>
        </div>
      </div>

      <!-- Chat messages -->
      <div id="chat-body"></div>

      <!-- Input bar -->
      <div id="chat-input-container">
        <input id="chat-input" placeholder="Type your message..." autocomplete="off" />
        <button id="chat-send" title="Send message">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>
      </div>

      <!-- Footer -->
      <div id="chat-footer">Powered by ${config.concept} AI Assistant</div>

      <!-- Full-screen loader overlay -->
      <div class="chat-loader" role="status" aria-live="polite">
        <div class="chat-loader-inner">
          <div class="chat-spinner"></div>
          <div class="chat-loader-text">Please wait...</div>
        </div>
      </div>`

    document.body.appendChild(chatWindow)

    chatWindow.querySelector("#close-chat").onclick = () => {
      chatWindow.classList.remove("open")
    }

    return chatWindow
  }

  // Resolve session — profile is pre-fetched inside resolveSession() on success.
  resolveSession()

  if (document.readyState === "loading") {
    window.addEventListener("DOMContentLoaded", initChatWidget)
  } else {
    initChatWidget()
  }
})()
