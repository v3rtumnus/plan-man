/* Ziel & Verlauf: weight (daily + 7-day trend), waist and push-ups over 4 weeks, 3 months or since the goal began. */
(function () {
  "use strict";

  const source = document.getElementById("health-chart-data");
  if (!source || typeof Chart === "undefined") {
    return;
  }
  const data = JSON.parse(source.textContent || "{}");
  const DAY = 24 * 60 * 60 * 1000;
  const time = (iso) => new Date(iso + "T00:00:00").getTime();
  const today = time(data.today);
  const fmt = new Intl.DateTimeFormat("de-AT", { day: "numeric", month: "numeric" });
  const num = (digits) => new Intl.NumberFormat("de-AT", { minimumFractionDigits: digits, maximumFractionDigits: digits });

  const BLUE = "#4154f1";
  const GREEN = "#2eca6a";
  const GREY = "#9aa6c2";

  const points = (list) => (list || []).map((p) => ({ x: time(p.date), y: Number(p.value) }));

  function line(canvasId, datasets, digits) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
      return null;
    }
    return new Chart(canvas, {
      type: "line",
      data: { datasets: datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        parsing: false,
        interaction: { mode: "nearest", intersect: false },
        plugins: {
          legend: { display: datasets.length > 1, labels: { boxWidth: 12 } },
          tooltip: {
            callbacks: {
              title: (items) => (items.length ? fmt.format(new Date(items[0].parsed.x)) : ""),
              label: (item) => item.dataset.label + ": " + num(digits).format(item.parsed.y),
            },
          },
          zoom: false,
        },
        scales: {
          x: {
            type: "linear",
            ticks: { callback: (value) => fmt.format(new Date(value)), maxTicksLimit: 7 },
          },
          y: { ticks: { callback: (value) => num(digits).format(value) } },
        },
      },
    });
  }

  function targetLine(label, value) {
    return {
      label: label,
      data: [],
      borderColor: GREY,
      borderDash: [6, 4],
      borderWidth: 1,
      pointRadius: 0,
      _target: value,
    };
  }

  const weightTarget = data.targetKg != null ? targetLine("Ziel", Number(data.targetKg)) : null;
  const pushUpsTarget = data.pushUpsTarget != null ? targetLine("Ziel", Number(data.pushUpsTarget)) : null;

  const charts = [
    {
      chart: line("chart-weight", [
        { label: "Tageswert", data: [], borderColor: GREY, backgroundColor: GREY, showLine: false, pointRadius: 3, _all: points(data.weight) },
        { label: "Trend", data: [], borderColor: BLUE, backgroundColor: BLUE, pointRadius: 0, borderWidth: 2, tension: 0.2, _all: points(data.trend) },
      ].concat(weightTarget ? [weightTarget] : []), 1),
    },
    {
      chart: line("chart-waist", [
        { label: "Taille", data: [], borderColor: GREEN, backgroundColor: GREEN, pointRadius: 4, _all: points(data.waist) },
      ], 1),
    },
    {
      chart: line("chart-pushups", [
        { label: "Liegestütze", data: [], borderColor: BLUE, backgroundColor: BLUE, pointRadius: 4, _all: points(data.pushUps) },
      ].concat(pushUpsTarget ? [pushUpsTarget] : []), 0),
    },
  ];

  function show(range) {
    let from;
    if (range === "goal" && data.goalStart) {
      from = time(data.goalStart);
    } else {
      from = today - (range === "goal" ? 91 : Number(range)) * DAY;
    }
    charts.forEach(({ chart }) => {
      if (!chart) {
        return;
      }
      chart.data.datasets.forEach((ds) => {
        if (ds._target != null) {
          ds.data = [{ x: from, y: ds._target }, { x: today, y: ds._target }];
        } else {
          ds.data = ds._all.filter((p) => p.x >= from && p.x <= today);
        }
      });
      chart.options.scales.x.min = from;
      chart.options.scales.x.max = today;
      chart.update();
    });
  }

  const ranges = document.getElementById("chart-range");
  if (ranges) {
    ranges.addEventListener("change", (event) => show(event.target.value));
  }
  show("28");
})();
