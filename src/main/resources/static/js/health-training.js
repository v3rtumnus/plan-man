/* Training page: live kcal on the Freeletics button, spinner while the AI estimates. */
(function () {
  "use strict";

  const form = document.getElementById("freeletics-form");
  if (form) {
    const kg = parseFloat(form.dataset.bodyKg) || 83;
    const met = {
      LIGHT: parseFloat(form.dataset.metLight),
      MEDIUM: parseFloat(form.dataset.metMedium),
      HARD: parseFloat(form.dataset.metHard),
    };
    const out = document.getElementById("fl-kcal");
    // Same formula as TrainingService.netKcal: (MET − 1) × kg × hours, rounded to 10.
    const update = () => {
      const minutes = parseFloat(form.querySelector("input[name=minutes]:checked").value);
      const intensity = form.querySelector("input[name=intensity]:checked").value;
      const kcal = Math.round((Math.max(0, met[intensity] - 1) * kg * minutes) / 60 / 10) * 10;
      out.textContent = "+" + kcal;
    };
    form.addEventListener("change", update);
    update();
  }

  const other = document.getElementById("other-form");
  if (other) {
    other.addEventListener("submit", () => {
      const button = other.querySelector("button[type=submit]");
      button.disabled = true;
      button.querySelector(".spinner-border").classList.remove("d-none");
      button.querySelector(".label").textContent = "Schätze …";
    });
  }
})();
