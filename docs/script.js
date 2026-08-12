(function () {
  "use strict";

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- footer year ---------- */
  var yearEl = document.getElementById("year");
  if (yearEl) yearEl.textContent = new Date().getFullYear();

  /* ---------- mobile nav ---------- */
  var toggle = document.getElementById("navToggle");
  var mobileNav = document.getElementById("mobile-nav");

  if (toggle && mobileNav) {
    toggle.addEventListener("click", function () {
      var isOpen = mobileNav.classList.toggle("is-open");
      toggle.setAttribute("aria-expanded", String(isOpen));
    });

    mobileNav.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", function () {
        mobileNav.classList.remove("is-open");
        toggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  /* ---------- scroll reveal ---------- */
  var revealEls = document.querySelectorAll(".reveal");

  if (reduceMotion || !("IntersectionObserver" in window)) {
    revealEls.forEach(function (el) { el.classList.add("is-visible"); });
  } else {
    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -40px 0px" }
    );
    revealEls.forEach(function (el) { observer.observe(el); });
  }

  /* ---------- ambient dot-network canvas ---------- */
  var canvas = document.getElementById("network-canvas");
  if (!canvas || !canvas.getContext) return;

  var ctx = canvas.getContext("2d");
  var nodes = [];
  var width, height, dpr;
  var rafId = null;

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = window.innerWidth;
    height = window.innerHeight;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    canvas.style.width = width + "px";
    canvas.style.height = height + "px";
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    seed();
  }

  function seed() {
    var area = width * height;
    var count = Math.max(18, Math.min(46, Math.round(area / 42000)));
    nodes = [];
    for (var i = 0; i < count; i++) {
      nodes.push({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 0.12,
        vy: (Math.random() - 0.5) * 0.12,
        r: Math.random() * 1.2 + 0.6
      });
    }
  }

  var linkDist = 150;

  function step() {
    ctx.clearRect(0, 0, width, height);

    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      n.x += n.vx;
      n.y += n.vy;

      if (n.x < -20 || n.x > width + 20) n.vx *= -1;
      if (n.y < -20 || n.y > height + 20) n.vy *= -1;
    }

    for (var a = 0; a < nodes.length; a++) {
      for (var b = a + 1; b < nodes.length; b++) {
        var dx = nodes[a].x - nodes[b].x;
        var dy = nodes[a].y - nodes[b].y;
        var dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < linkDist) {
          var alpha = (1 - dist / linkDist) * 0.16;
          ctx.strokeStyle = "rgba(140,155,255," + alpha + ")";
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(nodes[a].x, nodes[a].y);
          ctx.lineTo(nodes[b].x, nodes[b].y);
          ctx.stroke();
        }
      }
    }

    for (var j = 0; j < nodes.length; j++) {
      var node = nodes[j];
      ctx.fillStyle = "rgba(170,180,255,0.55)";
      ctx.beginPath();
      ctx.arc(node.x, node.y, node.r, 0, Math.PI * 2);
      ctx.fill();
    }

    rafId = requestAnimationFrame(step);
  }

  function drawStatic() {
    ctx.clearRect(0, 0, width, height);
    for (var a = 0; a < nodes.length; a++) {
      for (var b = a + 1; b < nodes.length; b++) {
        var dx = nodes[a].x - nodes[b].x;
        var dy = nodes[a].y - nodes[b].y;
        var dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < linkDist) {
          var alpha = (1 - dist / linkDist) * 0.14;
          ctx.strokeStyle = "rgba(140,155,255," + alpha + ")";
          ctx.beginPath();
          ctx.moveTo(nodes[a].x, nodes[a].y);
          ctx.lineTo(nodes[b].x, nodes[b].y);
          ctx.stroke();
        }
      }
    }
    for (var j = 0; j < nodes.length; j++) {
      ctx.fillStyle = "rgba(170,180,255,0.5)";
      ctx.beginPath();
      ctx.arc(nodes[j].x, nodes[j].y, nodes[j].r, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  var resizeTimer;
  window.addEventListener("resize", function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      resize();
      if (reduceMotion) drawStatic();
    }, 150);
  });

  resize();

  if (reduceMotion) {
    drawStatic();
  } else {
    document.addEventListener("visibilitychange", function () {
      if (document.hidden) {
        if (rafId) cancelAnimationFrame(rafId);
        rafId = null;
      } else if (!rafId) {
        step();
      }
    });
    step();
  }
})();
