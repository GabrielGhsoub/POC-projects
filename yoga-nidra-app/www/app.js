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

  function play(id) {
    const s = all().find((x) => x.id === id);
    if (!s) return;
    plays[id] = (plays[id] || 0) + 1;
    save(KEY_PLAYS, plays);
    const src = `${embedHost}/embed/${encodeURIComponent(id)}?autoplay=1&playsinline=1&rel=0&modestbranding=1`;
    frame.innerHTML = `<iframe src="${src}" title="${esc(s.title)}" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe>`;
    $("#playing-title").textContent = s.title || "Untitled";
    $("#playing-teacher").textContent = [s.teacher, s.minutes ? `${s.minutes} min` : null].filter(Boolean).join(" · ");
    $("#open-youtube").href = watchUrl(id);
    player.classList.remove("hidden");
    player.scrollIntoView({ behavior: "smooth", block: "start" });
    render();
  }

  function closePlayer() {
    frame.innerHTML = "";
    player.classList.add("hidden");
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

  fillTeachers();
  render();
})();
