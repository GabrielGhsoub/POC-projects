(function () {
  "use strict";

  const KEY_FAVS = "nidra.favs";
  const KEY_CUSTOM = "nidra.custom";
  const KEY_PLAYS = "nidra.plays";

  const $ = (sel) => document.querySelector(sel);
  const list = $("#list");
  const empty = $("#empty");
  const player = $("#player");
  const frame = $("#frame");
  const teacherSelect = $("#teacher-select");

  const load = (k, d) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } };
  const save = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} };

  let favs = new Set(load(KEY_FAVS, []));
  let custom = load(KEY_CUSTOM, []);
  let plays = load(KEY_PLAYS, {});
  let filter = { min: 0, fav: false, teacher: "" };

  const builtIn = (window.NIDRA_SESSIONS || []).map((s) => ({ ...s, builtIn: true }));
  const all = () => builtIn.concat(custom);

  // Inside an Android WebView (Capacitor) YouTube's embed refuses to play if the
  // referer isn't a real web origin, so only use the privacy-enhanced domain on the web.
  const isNative = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
  const embedHost = isNative ? "https://www.youtube.com" : "https://www.youtube-nocookie.com";

  // Wake-up alarm: the app's own native alarm (plugins/nidra-alarm). It rings on
  // the alarm stream with a full-screen alarm, even with the screen off or the app
  // closed, so it wakes you if you fell asleep during the session.
  const ALARM_LABEL = "Yoga Nidra wake-up";
  const ALARM_DELAY_MS = 3 * 60 * 1000;
  const KEY_SETUP_SKIPPED = "nidra.alarmSetupSkipped";
  const canAlarm = isNative && typeof window.Capacitor.nativePromise === "function" &&
    window.Capacitor.isPluginAvailable && window.Capacitor.isPluginAvailable("NidraAlarm");
  const nativeAlarm = (method, opts = {}) => window.Capacitor.nativePromise("NidraAlarm", method, opts);
  let alarm = null; // { at: Date } while one is pending

  // What the alarm needs, asked for once each, in this order.
  const ALARM_SETUP = [
    { key: "notifications", title: "Allow notifications?",
      text: "The alarm rings through a notification with Snooze and I'm awake buttons." },
    { key: "exactAlarm", title: "Allow exact alarms?",
      text: "Lets the alarm ring at the exact time instead of whenever Android gets around to it." },
    { key: "fullScreen", title: "Allow full-screen alarm?",
      text: "Lets the alarm turn the screen on and show over the lock screen, like the Clock app." },
    { key: "battery", title: "Turn off battery limits?",
      text: "Stops Samsung's battery saving from delaying or blocking the alarm." },
    { key: "dnd", title: "Allow Do Not Disturb access?",
      text: "Lets Yoga Nidra make sure Do Not Disturb lets alarms through. Find Yoga Nidra in the list and switch it on." }
  ];

  const thumb = (id) => `https://i.ytimg.com/vi/${id}/mqdefault.jpg`;
  const watchUrl = (id) => `https://www.youtube.com/watch?v=${id}`;

  function parseYouTubeId(url) {
    try {
      const u = new URL(url.trim());
      if (u.hostname === "youtu.be") return u.pathname.slice(1, 12);
      if (u.hostname.endsWith("youtube.com")) {
        if (u.searchParams.get("v")) return u.searchParams.get("v").slice(0, 11);
        const m = u.pathname.match(/\/(?:embed|shorts|live)\/([A-Za-z0-9_-]{11})/);
        if (m) return m[1];
      }
    } catch {}
    const m = url.match(/([A-Za-z0-9_-]{11})/);
    return m ? m[1] : null;
  }

  function fillTeachers() {
    const teachers = [...new Set(all().map((s) => s.teacher).filter(Boolean))].sort();
    teacherSelect.innerHTML = '<option value="">All teachers</option>' +
      teachers.map((t) => `<option value="${esc(t)}">${esc(t)}</option>`).join("");
    teacherSelect.value = teachers.includes(filter.teacher) ? filter.teacher : "";
  }

  function esc(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  function visible() {
    return all().filter((s) => {
      if (filter.fav && !favs.has(s.id)) return false;
      if (filter.teacher && s.teacher !== filter.teacher) return false;
      if (filter.min) {
        const m = Number(s.minutes) || 0;
        if (filter.min === 30 ? m < 30 : Math.abs(m - filter.min) > 4) return false;
      }
      return true;
    });
  }

  function render() {
    const items = visible();
    empty.classList.toggle("hidden", items.length > 0);
    list.innerHTML = items.map((s) => {
      const meta = [s.teacher, s.minutes ? `${s.minutes} min` : null, plays[s.id] ? `played ${plays[s.id]}×` : null]
        .filter(Boolean).join(" · ");
      return `
      <li class="card" data-id="${esc(s.id)}">
        <img class="thumb" src="${thumb(s.id)}" alt="" loading="lazy" data-act="play" onerror="this.style.visibility='hidden'">
        <div class="card-body">
          <h3 class="card-title">${esc(s.title || "Untitled")}</h3>
          <p class="card-meta">${esc(meta)}</p>
          ${s.note ? `<p class="card-note">${esc(s.note)}</p>` : ""}
          <div class="card-actions">
            <button class="btn" type="button" data-act="play">▶ Play here</button>
            <a class="btn ghost" href="${watchUrl(s.id)}" target="_blank" rel="noopener" data-act="yt">YouTube</a>
            <button class="btn ghost icon fav ${favs.has(s.id) ? "on" : ""}" type="button" data-act="fav" aria-label="Favourite">★</button>
            ${s.builtIn ? "" : `<button class="btn ghost icon danger" type="button" data-act="del" aria-label="Remove">✕</button>`}
          </div>
        </div>
      </li>`;
    }).join("");
  }

  const fmtTime = (d) => d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const fmtLength = (sec) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;
  const sessionSeconds = (s) => Number(s.seconds) || (Number(s.minutes) || 0) * 60;

  // Small yes/no sheet; resolves true for yes, false for no or dismiss.
  function ask(title, text, yes, no) {
    const dlg = $("#ask");
    $("#ask-title").textContent = title;
    $("#ask-text").textContent = text;
    $("#ask-yes").textContent = yes;
    $("#ask-no").textContent = no;
    return new Promise((resolve) => {
      const done = (v) => { dlg.onclose = null; if (dlg.open) dlg.close(); resolve(v); };
      $("#ask-yes").onclick = () => done(true);
      $("#ask-no").onclick = () => done(false);
      dlg.onclose = () => done(false);
      dlg.showModal();
    });
  }

  function showAlarmNote() {
    $("#alarm-note").classList.toggle("hidden", !alarm);
    if (alarm) $("#alarm-note-text").textContent = `⏰ Alarm set for ${fmtTime(alarm.at)}`;
  }

  // Walk through any missing alarm permissions. Settings screens are polled until
  // the permission shows up, or the user taps Done / Skip.
  async function ensureAlarmSetup() {
    const skipped = new Set(load(KEY_SETUP_SKIPPED, []));
    let state = await nativeAlarm("checkSetup");
    for (const item of ALARM_SETUP) {
      if (state[item.key] || skipped.has(item.key)) continue;
      if (!(await ask(item.title, item.text, "Allow", "Skip"))) {
        skipped.add(item.key);
        save(KEY_SETUP_SKIPPED, [...skipped]);
        continue;
      }
      state = await nativeAlarm("requestSetup", { item: item.key });
      if (state[item.key] || item.key === "notifications") continue;
      let timer;
      const granted = new Promise((resolve) => {
        timer = setInterval(async () => {
          try { if ((await nativeAlarm("checkSetup"))[item.key]) resolve(true); } catch {}
        }, 1000);
      });
      const waiting = ask("Allow it in Settings", "Switch it on, then come back here.", "Done", "Skip");
      await Promise.race([granted.then(() => { const d = $("#ask"); if (d.open) d.close(); }), waiting]);
      clearInterval(timer);
      state = await nativeAlarm("checkSetup");
    }
  }

  async function setAlarm(at) {
    try {
      await ensureAlarmSetup();
      await nativeAlarm("set", { at: at.getTime(), label: ALARM_LABEL });
      alarm = { at };
    } catch (e) {
      alert("Couldn't set the alarm: " + (e && e.message ? e.message : e));
    }
  }

  async function cancelAlarm() {
    if (!alarm) return;
    try { await nativeAlarm("cancel"); } catch {}
    alarm = null;
    showAlarmNote();
  }

  async function play(id) {
    const s = all().find((x) => x.id === id);
    if (!s) return;
    const sec = sessionSeconds(s);
    if (canAlarm && sec) {
      if (alarm) await cancelAlarm();
      const wantAlarm = await ask(
        "Wake-up alarm?",
        `Rings 3 minutes after this ${fmtLength(sec)} session ends, around ${fmtTime(new Date(Date.now() + sec * 1000 + ALARM_DELAY_MS))}, in case you fall asleep.`,
        "Set alarm", "No alarm"
      );
      // Timed from when you answer, so the wait doesn't eat into the 3 minutes.
      if (wantAlarm) await setAlarm(new Date(Date.now() + sec * 1000 + ALARM_DELAY_MS));
    }
    plays[id] = (plays[id] || 0) + 1;
    save(KEY_PLAYS, plays);
    const src = `${embedHost}/embed/${encodeURIComponent(id)}?autoplay=1&playsinline=1&rel=0&modestbranding=1`;
    frame.innerHTML = `<iframe src="${src}" title="${esc(s.title)}" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe>`;
    $("#playing-title").textContent = s.title || "Untitled";
    $("#playing-teacher").textContent = [s.teacher, s.minutes ? `${s.minutes} min` : null].filter(Boolean).join(" · ");
    $("#open-youtube").href = watchUrl(id);
    showAlarmNote();
    player.classList.remove("hidden");
    player.scrollIntoView({ behavior: "smooth", block: "start" });
    render();
  }

  async function closePlayer() {
    frame.innerHTML = "";
    player.classList.add("hidden");
    if (alarm && alarm.at > Date.now() &&
        await ask("Cancel the alarm too?", `Your wake-up alarm is set for ${fmtTime(alarm.at)}.`, "Cancel alarm", "Keep it")) {
      await cancelAlarm();
    }
  }

  list.addEventListener("click", (e) => {
    const el = e.target.closest("[data-act]");
    if (!el) return;
    const id = el.closest(".card").dataset.id;
    const act = el.dataset.act;
    if (act === "play") play(id);
    else if (act === "fav") { favs.has(id) ? favs.delete(id) : favs.add(id); save(KEY_FAVS, [...favs]); render(); }
    else if (act === "del") { custom = custom.filter((c) => c.id !== id); save(KEY_CUSTOM, custom); fillTeachers(); render(); }
  });

  $("#close-player").addEventListener("click", closePlayer);
  $("#alarm-cancel").addEventListener("click", cancelAlarm);

  $("#duration-chips").addEventListener("click", (e) => {
    const chip = e.target.closest(".chip");
    if (!chip) return;
    document.querySelectorAll(".chip").forEach((c) => c.classList.remove("active"));
    chip.classList.add("active");
    filter.fav = chip.dataset.fav === "1";
    filter.min = filter.fav ? 0 : Number(chip.dataset.min || 0);
    render();
  });

  teacherSelect.addEventListener("change", () => { filter.teacher = teacherSelect.value; render(); });

  $("#add-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const id = parseYouTubeId($("#add-url").value);
    if (!id) { alert("That doesn't look like a YouTube link."); return; }
    if (all().some((s) => s.id === id)) { alert("Already in your list."); return; }
    custom.push({
      id,
      title: $("#add-title").value.trim() || "My session",
      teacher: $("#add-teacher").value.trim() || "Added by me",
      minutes: Number($("#add-minutes").value) || 0,
      tags: ["custom"]
    });
    save(KEY_CUSTOM, custom);
    e.target.reset();
    fillTeachers();
    render();
  });

  if ("serviceWorker" in navigator && !isNative && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }

  // Pick up an alarm that is still pending from before the app was closed.
  if (canAlarm) {
    nativeAlarm("status").then((r) => { if (r && r.at) { alarm = { at: new Date(r.at) }; showAlarmNote(); } }).catch(() => {});
  }

  fillTeachers();
  render();
})();
