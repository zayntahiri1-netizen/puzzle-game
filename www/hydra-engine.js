/* ══════════════════════════════════════════════════════════════════════
   🐉 HYDRA ENGINE — محرّك اللعب الهجين لـ Decipher Tahiro
   ----------------------------------------------------------------------
   توليفة قوية للعب عبر الإنترنت من جميع أنحاء العالم.
   الفلسفة: Firebase هو "مصدر الحقيقة" (الحالة الموثوقة)، والباقي طبقة
   فوقه للسرعة والحضور والـ failover. كل المزوّدين خلف واجهة واحدة: HydraNet.

   الأدوار (Roles):
   ┌────────────┬──────────────────────────────────────────────┐
   │ Firebase   │ مصدر الحقيقة: الغرف، الحالة، النتائج (موثوق)  │
   │ Supabase   │ الحضور (presence) + قائمة الغرف الحية + Realtime│
   │ Ably       │ بثّ الحركات اللحظية (channel سريع <50ms)       │
   │ PubNub     │ قناة احتياطية للبثّ (failover لـ Ably)         │
   │ Photon     │ غرف لعب احترافية (اختياري، عند توفّر SDK)      │
   │ PeerJS     │ اتصال P2P مباشر (يقلّل زمن الوصول والتكلفة)    │
   └────────────┴──────────────────────────────────────────────┘

   ⚠️ أمان: ضع هنا فقط مفاتيح "عامة/عميل". مفتاح Ably يجب أن يكون
   بصلاحية publish/subscribe فقط (ليس admin). انظر README القسم الأمني.
   ══════════════════════════════════════════════════════════════════════ */
(function (global) {
  'use strict';

  // ─────────────────────────────────────────────────────────────
  // 1) الإعدادات — ضع مفاتيحك هنا (عامة فقط)
  // ─────────────────────────────────────────────────────────────
  const HYDRA_CONFIG = {
    photon: {
      appId: 'b79c00aa-7cc9-4082-8fb4-e11a6ec6b680',
      region: 'eu', // أقرب منطقة؛ غيّرها حسب جمهورك
      enabled: true
    },
    ably: {
      // ⚠️ هذا مفتاح admin — يعمل فوراً. للأمان استبدله بمفتاح publish/subscribe فقط لاحقاً.
      key: 'Q5lYAQ._zwMaw:Ynj1E7ejtPC7NrFG5QgcMqCEWCOGTqhyhgxKhitzcI8',
      enabled: true
    },
    pubnub: {
      publishKey: 'pub-c-8856e527-2542-4190-b998-a7b53e7d2f74',
      subscribeKey: 'sub-c-d943df62-83da-4314-8144-4ce888e8df2e',
      enabled: true
    },
    supabase: {
      url: 'https://khouuouyrqbqinbuqtzq.supabase.co',
      anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imtob3V1b3V5cnFicWluYnVxdHpxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIzMjcxMTUsImV4cCI6MjA5NzkwMzExNX0.3w8Qb6QjrFH7VUA00HCWE6gNuwN1ujDS2uuVSFVZ3TU',
      enabled: true
    },
    peerjs: { enabled: true }
  };

  // مصادر تحميل الـ SDKs (تُحمّل كسولاً عند الحاجة فقط)
  const SDK_URLS = {
    ably:     'https://cdn.ably.com/lib/ably.min-1.js',
    pubnub:   'https://cdn.pubnub.com/sdk/javascript/pubnub.9.2.0.min.js',
    supabase: 'https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2',
    peerjs:   'https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js',
    photon:   'https://cdn.jsdelivr.net/npm/photon-realtime@1.0.0/dist/photon.min.js'
  };

  // ─────────────────────────────────────────────────────────────
  // 2) أدوات مساعدة
  // ─────────────────────────────────────────────────────────────
  const _loaded = {};
  function loadScript(url) {
    if (_loaded[url]) return _loaded[url];
    _loaded[url] = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = url; s.async = true;
      s.onload = () => resolve(true);
      s.onerror = () => reject(new Error('SDK load failed: ' + url));
      document.head.appendChild(s);
    });
    return _loaded[url];
  }

  function log() { try { console.log.apply(console, ['🐉[Hydra]'].concat([].slice.call(arguments))); } catch (e) {} }
  function warn() { try { console.warn.apply(console, ['🐉[Hydra]'].concat([].slice.call(arguments))); } catch (e) {} }

  // مُوحّد للأحداث (Event Emitter بسيط)
  function Emitter() { this._h = {}; }
  Emitter.prototype.on = function (ev, fn) { (this._h[ev] = this._h[ev] || []).push(fn); return this; };
  Emitter.prototype.off = function (ev, fn) { if (this._h[ev]) this._h[ev] = this._h[ev].filter(f => f !== fn); };
  Emitter.prototype.emit = function (ev) {
    const args = [].slice.call(arguments, 1);
    (this._h[ev] || []).forEach(fn => { try { fn.apply(null, args); } catch (e) { warn('handler error', e); } });
  };

  // ─────────────────────────────────────────────────────────────
  // 3) محوّلات المزوّدين (Adapters) — كلٌّ يوفّر pub/sub موحّد
  // ─────────────────────────────────────────────────────────────

  // ── Ably ──
  const AblyAdapter = {
    name: 'ably', ready: false, client: null, channels: {},
    async init() {
      if (!HYDRA_CONFIG.ably.enabled) return false;
      if (HYDRA_CONFIG.ably.key.indexOf('REPLACE_WITH') === 0) { warn('Ably key not set — skipping'); return false; }
      try {
        await loadScript(SDK_URLS.ably);
        this.client = new global.Ably.Realtime({ key: HYDRA_CONFIG.ably.key, clientId: HydraNet.playerId });
        await new Promise((res, rej) => {
          this.client.connection.once('connected', res);
          this.client.connection.once('failed', rej);
          setTimeout(rej, 6000);
        });
        this.ready = true; log('Ably connected'); return true;
      } catch (e) { warn('Ably init failed', e.message); return false; }
    },
    sub(room, cb) {
      if (!this.ready) return;
      const ch = this.client.channels.get('dt:' + room);
      this.channels[room] = ch;
      ch.subscribe('move', msg => cb(msg.data));
    },
    pub(room, data) { if (this.ready && this.channels[room]) this.channels[room].publish('move', data); },
    unsub(room) { if (this.channels[room]) { this.channels[room].unsubscribe(); delete this.channels[room]; } }
  };

  // ── PubNub (failover للبثّ) ──
  const PubNubAdapter = {
    name: 'pubnub', ready: false, client: null, subs: {},
    async init() {
      if (!HYDRA_CONFIG.pubnub.enabled) return false;
      try {
        await loadScript(SDK_URLS.pubnub);
        this.client = new global.PubNub({
          publishKey: HYDRA_CONFIG.pubnub.publishKey,
          subscribeKey: HYDRA_CONFIG.pubnub.subscribeKey,
          userId: HydraNet.playerId
        });
        this.ready = true; log('PubNub ready'); return true;
      } catch (e) { warn('PubNub init failed', e.message); return false; }
    },
    sub(room, cb) {
      if (!this.ready) return;
      const ch = 'dt_' + room;
      const listener = { message: m => { if (m.channel === ch) cb(m.message); } };
      this.client.addListener(listener);
      this.client.subscribe({ channels: [ch] });
      this.subs[room] = { ch, listener };
    },
    pub(room, data) { if (this.ready) this.client.publish({ channel: 'dt_' + room, message: data }); },
    unsub(room) {
      if (this.subs[room]) {
        this.client.removeListener(this.subs[room].listener);
        this.client.unsubscribe({ channels: [this.subs[room].ch] });
        delete this.subs[room];
      }
    }
  };

  // ── Supabase (الحضور + قائمة الغرف الحية) ──
  const SupabaseAdapter = {
    name: 'supabase', ready: false, client: null, presenceCh: null,
    async init() {
      if (!HYDRA_CONFIG.supabase.enabled) return false;
      try {
        await loadScript(SDK_URLS.supabase);
        this.client = global.supabase.createClient(HYDRA_CONFIG.supabase.url, HYDRA_CONFIG.supabase.anonKey);
        this.ready = true; log('Supabase ready'); return true;
      } catch (e) { warn('Supabase init failed', e.message); return false; }
    },
    // حضور عالمي: من المتصل الآن
    joinPresence(meta) {
      if (!this.ready) return;
      this.presenceCh = this.client.channel('dt:lobby', { config: { presence: { key: HydraNet.playerId } } });
      this.presenceCh
        .on('presence', { event: 'sync' }, () => {
          const state = this.presenceCh.presenceState();
          const online = Object.keys(state).length;
          HydraNet.emit('presence', { online, state });
        })
        .subscribe(async (status) => {
          if (status === 'SUBSCRIBED') await this.presenceCh.track(meta || { name: HydraNet.playerName, at: Date.now() });
        });
    },
    leavePresence() { if (this.presenceCh) { this.presenceCh.unsubscribe(); this.presenceCh = null; } }
  };

  // ── PeerJS (P2P مباشر) ──
  const PeerAdapter = {
    name: 'peerjs', ready: false, peer: null, conns: {},
    async init() {
      if (!HYDRA_CONFIG.peerjs.enabled) return false;
      try {
        await loadScript(SDK_URLS.peerjs);
        this.peer = new global.Peer('dt-' + HydraNet.playerId.replace(/[^a-zA-Z0-9]/g, ''));
        await new Promise((res, rej) => {
          this.peer.on('open', res); this.peer.on('error', rej); setTimeout(rej, 6000);
        });
        this.peer.on('connection', conn => {
          conn.on('data', d => HydraNet.emit('p2p', d));
          this.conns[conn.peer] = conn;
        });
        this.ready = true; log('PeerJS ready'); return true;
      } catch (e) { warn('PeerJS init failed', e.message); return false; }
    },
    connect(peerId) {
      if (!this.ready) return;
      const conn = this.peer.connect('dt-' + peerId.replace(/[^a-zA-Z0-9]/g, ''));
      conn.on('data', d => HydraNet.emit('p2p', d));
      this.conns[peerId] = conn;
    },
    send(data) { Object.values(this.conns).forEach(c => { try { if (c.open) c.send(data); } catch (e) {} }); }
  };

  // ── Photon (غرف لعب احترافية — متصل فعلياً) ──
  const PhotonAdapter = {
    name: 'photon', ready: false, client: null, _room: null, _cb: null,
    async init() {
      if (!HYDRA_CONFIG.photon.enabled) return false;
      try {
        await loadScript(SDK_URLS.photon);
        if (!global.Photon || !global.Photon.LoadBalancing) { warn('Photon SDK missing'); return false; }
        const LBC = global.Photon.LoadBalancing.LoadBalancingClient;
        const proto = (location.protocol === 'https:') ? 1 : 0; // 1 = wss
        this.client = new LBC(proto, HYDRA_CONFIG.photon.appId, '1.0');
        const self = this;
        this.client.onStateChange = function (state) {
          const S = global.Photon.LoadBalancing.LoadBalancingClient.State;
          if (state === S.JoinedLobby) { self.ready = true; }
        };
        // استقبال أحداث اللعب من Photon
        this.client.onEvent = function (code, content, actorNr) {
          if (code === 1 && self._cb) self._cb(content);
        };
        this.client.setLogLevel(global.Photon.LogLevel.ERROR);
        const ok = this.client.connectToRegionMaster(HYDRA_CONFIG.photon.region);
        if (!ok) { warn('Photon connect failed'); return false; }
        // انتظر دخول اللوبي (حتى 6 ثوانٍ)
        await new Promise((res) => {
          let n = 0;
          const iv = setInterval(() => {
            n++;
            if (self.ready) { clearInterval(iv); res(); }
            else if (n > 60) { clearInterval(iv); res(); }
          }, 100);
        });
        if (this.ready) { log('Photon connected (master+lobby)'); return true; }
        warn('Photon lobby timeout'); return false;
      } catch (e) { warn('Photon init failed', e.message); return false; }
    },
    sub(room, cb) {
      if (!this.ready) return;
      this._cb = cb; this._room = room;
      try {
        // انضم لغرفة Photon (أنشئها إن لم توجد)
        this.client.joinRoom('dt_' + room, { createIfNotExists: true });
      } catch (e) { warn('Photon joinRoom', e.message); }
    },
    pub(room, data) {
      if (!this.ready || !this.client.isJoinedToRoom()) return;
      try { this.client.raiseEvent(1, data, { receivers: global.Photon.LoadBalancing.Constants.ReceiverGroup.Others }); } catch (e) {}
    },
    unsub(room) { try { if (this.client && this.client.isJoinedToRoom()) this.client.leaveRoom(); } catch (e) {} this._cb = null; this._room = null; }
  };

  // ─────────────────────────────────────────────────────────────
  // 4) HydraNet — الواجهة الموحّدة التي يستخدمها كود اللعبة
  // ─────────────────────────────────────────────────────────────
  const HydraNet = new Emitter();
  HydraNet.playerId = 'p_' + Math.random().toString(36).slice(2, 10);
  HydraNet.playerName = 'Player';
  HydraNet.adapters = { ably: AblyAdapter, pubnub: PubNubAdapter, supabase: SupabaseAdapter, peerjs: PeerAdapter, photon: PhotonAdapter };
  HydraNet.status = { ably: false, pubnub: false, supabase: false, peerjs: false, photon: false };
  HydraNet._fastChannel = null;   // القناة الأساسية المعروضة
  HydraNet._fastChannels = [];    // كل القنوات النشطة (Ably + PubNub + Photon)
  HydraNet._seen = {};            // لإزالة تكرار الرسائل
  HydraNet._activeRoom = null;
  HydraNet._initialized = false;

  // تهيئة كل المزوّدين بالتوازي (لا يُسقط أحدهم البقية)
  HydraNet.init = async function (opts) {
    if (this._initialized) return this.status;
    opts = opts || {};
    if (opts.playerId) this.playerId = opts.playerId;
    if (opts.playerName) this.playerName = opts.playerName;

    const results = await Promise.allSettled([
      AblyAdapter.init(), PubNubAdapter.init(), SupabaseAdapter.init(),
      PeerAdapter.init(), PhotonAdapter.init()
    ]);
    this.status.ably     = results[0].status === 'fulfilled' && results[0].value === true;
    this.status.pubnub   = results[1].status === 'fulfilled' && results[1].value === true;
    this.status.supabase = results[2].status === 'fulfilled' && results[2].value === true;
    this.status.peerjs   = results[3].status === 'fulfilled' && results[3].value === true;
    this.status.photon   = results[4].status === 'fulfilled' && results[4].value === true;

    // قنوات البثّ السريع النشطة (كلها تعمل معاً للموثوقية القصوى)
    this._fastChannels = [];
    if (this.status.ably)   this._fastChannels.push(AblyAdapter);
    if (this.status.pubnub) this._fastChannels.push(PubNubAdapter);
    if (this.status.photon) this._fastChannels.push(PhotonAdapter);
    // القناة "الأساسية" المعروضة (الأسرع المتاح) لأغراض العرض فقط
    this._fastChannel = this._fastChannels[0] || null;

    this._initialized = true;
    this._seen = {}; // لمنع تكرار الرسائل القادمة من قنوات متعددة
    log('Initialized:', JSON.stringify(this.status),
        '| fast channels =', this._fastChannels.map(c => c.name).join('+') || 'firebase-only');
    this.emit('ready', this.status);
    return this.status;
  };

  // الانضمام للحضور العالمي (lobby presence)
  HydraNet.joinLobby = function (meta) { SupabaseAdapter.joinPresence(meta); };
  HydraNet.leaveLobby = function () { SupabaseAdapter.leavePresence(); };

  // مُعالِج استقبال موحّد مع إزالة التكرار (نفس الرسالة قد تصل من Ably و PubNub و Photon)
  HydraNet._onIncoming = function (data) {
    if (!data) return;
    if (data.from === this.playerId) return;           // تجاهل رسائلنا
    const id = (data.from || '') + ':' + (data.t || '');
    if (this._seen[id]) return;                         // وصلت من قناة أخرى مسبقاً
    this._seen[id] = Date.now();
    // نظّف الذاكرة من المعرّفات القديمة
    if (Math.random() < 0.05) {
      const now = Date.now();
      for (const k in this._seen) if (now - this._seen[k] > 30000) delete this._seen[k];
    }
    this.emit('move', data);
  };

  // الانضمام لغرفة بثّ سريع عبر كل القنوات النشطة (فوق Firebase)
  HydraNet.joinRoom = function (roomId) {
    this._activeRoom = roomId;
    const self = this;
    this._fastChannels.forEach(function (ch) {
      try { ch.sub(roomId, function (data) { self._onIncoming(data); }); } catch (e) { warn(ch.name + ' sub failed', e.message); }
    });
    log('joined room', roomId, 'via', this._fastChannels.map(c => c.name).join('+') || 'firebase-only');
  };

  // بثّ حركة لحظية عبر كل القنوات + P2P (موثوقية ووصول أقصى)
  HydraNet.sendMove = function (payload) {
    const data = Object.assign({ from: this.playerId, t: Date.now() }, payload);
    if (this._activeRoom) {
      this._fastChannels.forEach(function (ch) { try { ch.pub(HydraNet._activeRoom, data); } catch (e) {} });
    }
    if (this.status.peerjs) PeerAdapter.send(data);
  };

  // مغادرة الغرفة من كل القنوات
  HydraNet.leaveRoom = function () {
    const room = this._activeRoom;
    if (room) this._fastChannels.forEach(function (ch) { try { ch.unsub(room); } catch (e) {} });
    this._activeRoom = null;
  };

  // معلومات الحالة (لعرضها في واجهة debug إن رغبت)
  HydraNet.getStatus = function () {
    return Object.assign({
      fastChannels: (this._fastChannels || []).map(c => c.name)
    }, this.status);
  };

  global.HydraNet = HydraNet;
  global.HYDRA_CONFIG = HYDRA_CONFIG;
  log('🐉 Hydra Engine loaded — call HydraNet.init() to start');

})(typeof window !== 'undefined' ? window : this);
