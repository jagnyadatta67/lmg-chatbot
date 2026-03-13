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
    giftcardEnv: scriptTag?.getAttribute("data-giftcard-env") || window.CHATBOT_CONFIG?.giftcardEnv || "www",
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
      secondary: "#4A55E2",
      dark: "#1a1a1a",
      light: "#f8f9fa",
      logo: "https://assets-cloud.landmarkshops.in/website_images/in/logos/logo-max.svg",
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
      /* === CHATBOT WIDGET STYLES === */
      * {
        box-sizing: border-box;
      }

      /* Floating Button */
      #chatbot-button {
        position: fixed;
        bottom: 24px;
        right: 24px;
        width: 64px;
        height: 64px;
        border-radius: 50%;
        background: white;
        border: 3px solid ${theme.primary};
        cursor: pointer;
        z-index: 9999;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
        transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
        overflow: hidden;
      }

      #chatbot-button:hover {
        transform: scale(1.1);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16);
      }

      #chatbot-button:active {
        transform: scale(0.95);
      }

      .chat-fab-icon {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .chat-fab-icon img {
        width: 48px;
        height: auto;
        object-fit: contain;
      }

      .chat-fab-badge {
        position: absolute;
        bottom: -8px;
        right: -8px;
        background: ${theme.primary};
        color: white;
        border-radius: 50%;
        width: 32px;
        height: 32px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
        animation: pulse 2s infinite;
      }

      @keyframes pulse {
        0%, 100% { transform: scale(1); }
        50% { transform: scale(1.1); }
      }

      /* Chat Window */
      #chatbot-container {
        position: fixed;
        bottom: 100px;
        right: 24px;
        width: min(90vw, 420px);
        height: min(85vh, 680px);
        background: white;
        border-radius: 16px;
        box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
        display: none;
        flex-direction: column;
        overflow: hidden;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        z-index: 9999;
        animation: slideUp 0.3s ease-out;
      }

      #chatbot-container.open {
        display: flex;
        animation: slideUp 0.3s ease-out;
      }

      @keyframes slideUp {
        from {
          opacity: 0;
          transform: translateY(20px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }

      /* Header */
      .chat-header {
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        color: white;
        padding: 16px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        flex-shrink: 0;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      }

      .chat-header-content {
        display: flex;
        align-items: center;
        gap: 10px;
        flex: 1;
      }

     .chat-header-logo.max-concept {
        height: 24px;
        width: auto;
        object-fit: contain;
        filter: brightness(0) invert(1);
      }
      .chat-header-logo {
        height: 24px;
        width: auto;
        object-fit: contain;
       
      }

      .chat-header-title {
        font-size: 15px;
        font-weight: 600;
        letter-spacing: 0.3px;
      }

      #close-chat {
        cursor: pointer;
        font-size: 20px;
        opacity: 0.8;
        transition: opacity 0.2s;
        padding: 4px 8px;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      #close-chat:hover {
        opacity: 1;
      }

      /* Chat Body */
      #chat-body {
        flex: 1;
        overflow-y: auto;
        padding: 16px;
        display: flex;
        flex-direction: column;
        gap: 12px;
        background: ${theme.light};
      }

      #chat-body::-webkit-scrollbar {
        width: 6px;
      }

      #chat-body::-webkit-scrollbar-track {
        background: transparent;
      }

      #chat-body::-webkit-scrollbar-thumb {
        background: ${theme.primary};
        border-radius: 3px;
        opacity: 0.5;
      }

      /* Message Bubbles */
      .bubble {
        display: flex;
        animation: fadeIn 0.3s ease-out;
        word-wrap: break-word;
        max-width: 85%;
      }

      @keyframes fadeIn {
        from {
          opacity: 0;
          transform: translateY(8px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }

      .bot-bubble {
        align-self: flex-start;
        background: white;
        color: ${theme.dark};
        border-radius: 12px;
        padding: 10px 14px;
        font-size: 14px;
        line-height: 1.4;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
        border: 1px solid #e5e7eb;
      }

      .user-bubble {
        align-self: flex-end;
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        color: white;
        border-radius: 12px;
        padding: 10px 14px;
        font-size: 14px;
        line-height: 1.4;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
      }

      /* Input Container */
      #chat-input-container {
        display: none;
        border-top: 1px solid #e5e7eb;
        padding: 12px;
        gap: 8px;
        background: white;
        flex-shrink: 0;
      }

      #chat-input-container.active {
        display: flex;
      }

      #chat-input {
        flex: 1;
        padding: 10px 14px;
        border: 1px solid #e5e7eb;
        border-radius: 8px;
        font-size: 14px;
        font-family: inherit;
        outline: none;
        transition: border-color 0.2s;
        min-height: 40px;
      }

      #chat-input:focus {
        border-color: ${theme.primary};
        box-shadow: 0 0 0 3px rgba(${hexToRgb(theme.primary)}, 0.1);
      }

      #chat-send {
        background: linear-gradient(135deg, ${theme.primary}, ${theme.secondary});
        color: white;
        border: none;
        padding: 10px 16px;
        border-radius: 8px;
        cursor: pointer;
        font-weight: 600;
        font-size: 13px;
        transition: all 0.2s;
        flex-shrink: 0;
      }

      #chat-send:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      }

      #chat-send:active {
        transform: translateY(0);
      }

      /* Loader */
      .chat-loader {
        position: absolute;
        inset: 0;
        display: none;
        align-items: center;
        justify-content: center;
        background: rgba(255, 255, 255, 0.9);
        z-index: 9998;
        backdrop-filter: blur(2px);
      }

      .chat-loader.active {
        display: flex;
      }

      .chat-loader-inner {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12px;
      }

      .chat-spinner {
        width: 40px;
        height: 40px;
        border: 3px solid rgba(0, 0, 0, 0.08);
        border-top-color: ${theme.primary};
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
      }

      @keyframes spin {
        to { transform: rotate(360deg); }
      }

      .chat-loader-text {
        font-size: 13px;
        color: ${theme.dark};
        font-weight: 500;
      }

      /* Menu Buttons */
      .menu-btn, .submenu-btn, #back-to-menu-btn {
        width: 100%;
        padding: 12px 14px;
        margin: 6px 0;
        border: 1.5px solid ${theme.primary};
        border-radius: 10px;
        background: white;
        color: ${theme.primary};
        cursor: pointer;
        text-align: left;
        font-weight: 600;
        font-size: 14px;
        transition: all 0.2s;
        font-family: inherit;
      }

      .menu-btn:hover, .submenu-btn:hover, #back-to-menu-btn:hover {
        background: ${theme.light};
        transform: translateX(-2px);
      }

      .menu-btn:active, .submenu-btn:active, #back-to-menu-btn:active {
        transform: translateX(0);
      }

      .submenu-btn.dynamic {
        background: #f0f4ff;
      }

      /* Order Card */
      .order-card {
        background: white;
        border: 1px solid ${theme.primary};
        border-radius: 12px;
        padding: 12px;
        margin-top: 10px;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
        display: flex;
        gap: 12px;
      }

      .order-card-image {
        width: 80px;
        height: 80px;
        border-radius: 8px;
        object-fit: cover;
        border: 1px solid #e5e7eb;
        flex-shrink: 0;
      }

      .order-card-content {
        flex: 1;
        min-width: 0;
      }

      .order-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        margin-bottom: 6px;
      }

      .order-card-title {
        font-weight: 700;
        color: ${theme.dark};
        font-size: 14px;
      }

      .order-status-badge {
        display: inline-block;
        padding: 4px 8px;
        font-weight: 600;
        font-size: 11px;
        color: white;
        background: ${theme.primary};
        border-radius: 4px;
        white-space: nowrap;
      }

      .order-card-meta {
        font-size: 13px;
        color: #666;
        margin-bottom: 8px;
      }

      .order-products-strip {
        display: flex;
        gap: 6px;
        flex-wrap: wrap;
        margin-top: 8px;
        margin-bottom: 4px;
      }

      .order-product-thumb {
        width: 48px;
        height: 48px;
        border-radius: 6px;
        object-fit: cover;
        border: 1px solid #e5e7eb;
        flex-shrink: 0;
        background: #f3f4f6;
      }

      .order-card-actions {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        margin-top: 8px;
      }

      .order-btn {
        padding: 8px 12px;
        border: none;
        border-radius: 8px;
        cursor: pointer;
        font-size: 13px;
        font-weight: 600;
        transition: all 0.2s;
        font-family: inherit;
      }

      .order-btn-primary {
        background: ${theme.primary};
        color: white;
      }

      .order-btn-primary:hover {
        opacity: 0.9;
      }

      .order-btn-secondary {
        background: white;
        border: 1px solid ${theme.primary};
        color: ${theme.primary};
      }

      .order-btn-secondary:hover {
        background: ${theme.light};
      }

      /* Profile Card */
      .profile-card {
        background: white;
        border: 1px solid ${theme.primary};
        border-radius: 12px;
        padding: 14px;
        margin-top: 10px;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
      }

      .profile-field {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 8px 0;
        border-bottom: 1px solid #f0f0f0;
        font-size: 13px;
        line-height: 1.5;
      }

      .profile-field:last-child {
        border-bottom: none;
      }

      .profile-label {
        font-weight: 600;
        color: ${theme.dark};
        min-width: 80px;
        flex-shrink: 0;
      }

      .profile-value {
        color: #555;
        text-align: right;
        flex: 1;
        margin-left: 12px;
        word-break: break-word;
      }

      @media (max-width: 480px) {
        .profile-field {
          flex-direction: column;
          align-items: flex-start;
        }

        .profile-value {
          text-align: left;
          margin-left: 0;
          margin-top: 4px;
        }
      }

      /* Toast */
      #chat-copy-toast {
        position: fixed;
        bottom: 160px;
        right: 30px;
        background: ${theme.dark};
        color: white;
        padding: 10px 14px;
        border-radius: 8px;
        z-index: 10000;
        font-size: 13px;
        font-weight: 500;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
        animation: slideInUp 0.3s ease-out;
      }

      @keyframes slideInUp {
        from {
          opacity: 0;
          transform: translateY(10px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }

      /* Gift Card Input */
      .gift-card-input-container {
        display: flex;
        align-items: center;
        margin-top: 10px;
        gap: 8px;
      }

      .gift-card-input {
        flex: 1;
        padding: 10px 12px;
        border: 1px solid ${theme.primary};
        border-radius: 8px;
        font-size: 14px;
        outline: none;
        font-family: inherit;
      }

      .gift-card-input:focus {
        box-shadow: 0 0 0 3px rgba(${hexToRgb(theme.primary)}, 0.1);
      }

      .gift-card-btn {
        background: ${theme.primary};
        color: white;
        border: none;
        border-radius: 8px;
        padding: 10px 14px;
        cursor: pointer;
        font-weight: 600;
        font-size: 13px;
        transition: all 0.2s;
        font-family: inherit;
      }

      .gift-card-btn:hover {
        opacity: 0.9;
      }

      /* Responsive Design */
      @media (max-width: 480px) {
        #chatbot-button {
          bottom: 16px;
          right: 16px;
          width: 56px;
          height: 56px;
        }

        .chat-fab-icon img {
          width: 40px;
        }

        #chatbot-container {
          bottom: 80px;
          right: 12px;
          left: 12px;
          width: calc(100% - 24px);
          height: calc(100vh - 100px);
          border-radius: 12px;
        }

        .bubble {
          max-width: 90%;
        }

        #chat-body {
          padding: 12px;
          gap: 10px;
        }

        .order-card {
          flex-direction: column;
        }

        .order-card-image {
          width: 100%;
          height: 120px;
        }

        #chat-copy-toast {
          bottom: 100px;
          right: 16px;
          left: 16px;
        }
      }

      @media (max-width: 360px) {
        #chatbot-button {
          width: 52px;
          height: 52px;
        }

        .chat-fab-icon img {
          width: 36px;
        }

        .chat-header-title {
          font-size: 13px;
        }

        .bubble {
          max-width: 95%;
        }
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

    function showLoader(message = "Please wait...") {
      const loaderText = loader.querySelector(".chat-loader-text")
      if (loaderText) loaderText.textContent = message
      loader.classList.add("active")
    }

    function hideLoader() {
      loader.classList.remove("active")
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
            appId:       config.appid, // note: backend expects capital-I "appId"
            userId:      session.customerId,
            accessToken: session.accessToken,
            ...params,
          },
          "Finding stores...",
        )
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

    const INTENT_HANDLERS = {
      POLICY_QUESTION:    handleGeneralIntent,
      GENERAL_QUERY:      handleGeneralIntent,
      ORDER_TRACKING:     handleOrderTracking,
      CUSTOMER_PROFILE:   handleCustomerProfile,
      // ── new intents ────────────────────────────────────────────────────────
      ORDER_LISTING:      handleChatbotOrderList,
      DELIVERY_TRACKING:  handleDeliveryTrackingResponse,
      RETURN_STATUS:      handleReturnStatusResponse,
      WALLET_BALANCE:     handleWalletBalanceResponse,
      DEFAULT:            handleDefaultIntent,
    }

    function handleGeneralIntent(payload) {
      renderBotMessage(payload.chat_message || "No information found.")
      renderBackToMenu()
    }

    function handleDefaultIntent(payload) {
      renderBotMessage(payload.chat_message || payload.data || "No response available.")
      renderBackToMenu()
    }

    function handleOrderTracking(payload) {
      if (checkAndTriggerLogin(payload, "Please login to check your order details.")) return
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
    }

    function handleCustomerProfile(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to view your profile.")) return

      const chatMsg = (payload?.chat_message || payload?.chatMessage || "").trim()
      if (chatMsg !== "") {
        renderBotMessage(chatMsg)
        renderBackToMenu()
        return
      }

      // Prefer profile from intent response, fallback to pre-fetched session cache
      const p = payload?.customerProfile || session.profile
      if (!p) {
        renderBotMessage("Profile not available. Please try again.")
        renderBackToMenu()
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
        return
      }
      console.log("🧠 Chatbot Response:", json)
      const intent = json.intent || json.data?.intent || "DEFAULT"
      const payload = typeof json.data === "string" ? { chat_message: json.data } : json.data || json
      const handler = INTENT_HANDLERS[intent] || INTENT_HANDLERS.DEFAULT
      handler(payload)
    }

    async function showGreeting() {
      clearBody()
      inputContainer.classList.remove("active")

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
     * intentKey dispatch map.
     *
     * Each entry maps an intentKey string to an async handler function.
     * The widget calls the matching handler, or falls back to OPEN_INPUT for
     * any key that is not listed here (including null / undefined keys).
     *
     * To add a new integration, just add a new key → async function entry here.
     */
    const SUBMENU_INTENT_HANDLERS = {
      NEARBY_STORE:        async () => { await handleNearbyStore() },
      GIFT_CARD_BALANCE:   async () => { await handleGiftCardBalance() },
      TRACK_ORDER:         async () => { await handleOrderTrackMenu() },
      // ── new intents ────────────────────────────────────────────────────────
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
      const handler = SUBMENU_INTENT_HANDLERS[key]

      if (handler) {
        await handler()
      } else {
        // Unknown intentKey → free-text input fallback
        renderBotMessage(`Please enter your question related to <b>${sub.title}</b>.`)
        inputContainer.classList.add("active")
        sendButton.onclick = () => {
          const msg = inputField.value.trim()
          if (!msg) return
          renderUserMessage(msg)
          sendMessage(null, msg)
          inputField.value = ""
        }
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

      renderBotMessage(
        "📦 Please enter your <b>Order Number</b> to check its status.<br>" +
        "<small style='color:#888;'>Order numbers are numeric, e.g. <i>9419396447</i></small>"
      )

      inputContainer.classList.add("active")
      inputField.placeholder = "Enter order number (digits only)..."
      inputField.inputMode   = "numeric"
      inputField.focus()

      const handleSubmit = async () => {
        const raw = inputField.value.trim()

        // Validate: must be 7–12 digits only
        if (!/^\d{7,12}$/.test(raw)) {
          renderBotMessage("⚠️ That doesn't look like a valid order number. Please enter a numeric order number (e.g. 9419396447).")
          inputField.value = ""
          inputField.focus()
          return
        }

        renderUserMessage(raw)
        inputField.value          = ""
        inputField.inputMode      = ""
        inputField.placeholder    = "Type a message..."
        inputContainer.classList.remove("active")
        sendButton.onclick        = null   // detach this handler

        showLoader("Looking up order #" + raw + "…")
        const { data: json, error } = await api._post(
          "/chat",
          { ...api._context(), message: "Track order " + raw, orderNo: raw },
          null   // loader already shown above
        )
        hideLoader()

        if (error || !json) {
          renderBotMessage("⚠️ Unable to fetch order details. Please try again later.")
          renderBackToMenu()
          return
        }

        const payload = typeof json.data === "string" ? { chat_message: json.data } : json.data || json
        handleOrderTracking(payload)
      }

      // Wire submit to both the Send button and Enter key
      sendButton.onclick = handleSubmit
      inputField.onkeydown = (e) => {
        if (e.key === "Enter") handleSubmit()
      }
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
        const g = data?.giftCardDetails || data

        if (g?.errorOccurred) {
          const errorReason = g?.errors?.[0]?.message || ""
          if (errorReason === "lmg.giftcard.card.not.found") {
            renderBotMessage("❌ Invalid gift card number. Please check and try again.")
          } else if (errorReason === "lmg.giftcard.client.server.error") {
            renderBotMessage("⚠️ Gift card service is currently unavailable. Please try later.")
          } else {
            renderBotMessage("😔 Unable to fetch your gift card balance. Please try again later.")
          }
        } else if (g?.balanceAmount != null) {
          renderBotMessage(data.chat_message || "Here's your gift card balance:")
          chatBody.innerHTML += `
            <div class="bubble bot-bubble" style="border:1px solid ${theme.primary};">
              <b>Card Number:</b> ${g.cardNumber || "N/A"}<br/>
              <b>Status:</b> ${g.status || "N/A"}<br/>
              <b>Message:</b> ${g.message || "N/A"}<br/>
              <b>Balance:</b> ₹${g.balanceAmount?.toFixed(2) || "0.00"} ${g.currency || "INR"}
            </div>
          `
        } else {
          renderBotMessage("😔 Unable to fetch your gift card balance. Please try again later.")
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELIVERY TRACKING  (submenu entry-point + INTENT_HANDLER response renderer)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called when user taps "🚚 Delivery Status" submenu button. */
    async function handleDeliveryTrackingMenu() {
      renderBotMessage(
        "🚚 Please enter your <b>Order Number</b> (and optionally the item/entry number) " +
        "to check your delivery status. Example: <i>\"LS12345678 entry 1\"</i>"
      )
      inputContainer.classList.add("active")
      inputField.placeholder = "Enter order number..."
      inputField.focus()

      sendButton.onclick = () => {
        const msg = inputField.value.trim()
        if (!msg) return
        renderUserMessage(msg)
        inputField.value = ""
        inputContainer.classList.remove("active")
        inputField.placeholder = "Type a message..."
        sendMessage(null, msg)   // AI extracts params and calls DeliveryTrackingTool
      }
    }

    /**
     * Renders the DeliveryResponse returned by the DELIVERY_TRACKING intent.
     * Expected shape: { orderNo, item_details: [{ position, quantity, status, consignment, returnDetail }], chatMessage }
     */
    function handleDeliveryTrackingResponse(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to check your delivery status.")) return

      if (payload.chatMessage && payload.chatMessage.trim() !== "") {
        renderBotMessage(payload.chatMessage)
        renderBackToMenu()
        return
      }

      const items = Array.isArray(payload.item_details) ? payload.item_details : []
      if (items.length === 0) {
        renderBotMessage("🚚 No delivery details found for that order.")
        renderBackToMenu()
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETURN STATUS  (submenu entry-point + INTENT_HANDLER response renderer)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called when user taps "↩️ Return / Refund" submenu button. */
    async function handleReturnStatusMenu() {
      renderBotMessage(
        "↩️ Please enter your <b>Order Number</b> or <b>RMA Number</b> to check your return / refund status. " +
        "Example: <i>\"order LS12345678\"</i> or <i>\"RMA 99001\"</i>"
      )
      inputContainer.classList.add("active")
      inputField.placeholder = "Enter order or RMA number..."
      inputField.focus()

      sendButton.onclick = () => {
        const msg = inputField.value.trim()
        if (!msg) return
        renderUserMessage(msg)
        inputField.value = ""
        inputContainer.classList.remove("active")
        inputField.placeholder = "Type a message..."
        sendMessage(null, msg)   // AI extracts orderNo + rmaNo and calls ReturnStatusTool
      }
    }

    /**
     * Renders the ReturnStatusResponse returned by the RETURN_STATUS intent.
     * Expected shape: { consignmentCode, returnId, rma, returnStatus, returnCreationDate,
     *                   totalRefundAmount, faqLink, chatMessage }
     */
    function handleReturnStatusResponse(payload) {
      if (checkAndTriggerLogin(payload, "Please sign in to check your return status.")) return

      if (payload.chatMessage && payload.chatMessage.trim() !== "") {
        renderBotMessage(payload.chatMessage)
        renderBackToMenu()
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
     <div class="chat-header">
  <div class="chat-header-content">
    <img
      src="${theme.logo}"
      alt="${config.concept} logo"
      class="chat-header-logo ${config.concept === 'MAX' || config.concept === 'HOMECENTRE' ? 'max-concept' : ''}"
    />
    <span class="chat-header-title">Chat Service</span>
  </div>
  <span id="close-chat">✖</span>
</div>

      <div id="chat-body"></div>
      <div id="chat-input-container">
        <input id="chat-input" placeholder="Type your message..." />
        <button id="chat-send">Send</button>
      </div>
      <div id="chat-footer" style="text-align:center;font-size:12px;padding:8px;background:#fafafa;border-top:1px solid #eee;">
        Powered by ${config.concept}
      </div>
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
