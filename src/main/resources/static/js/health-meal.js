/* Health meal capture and review. Plain JS, no dependencies. */
(function () {
  "use strict";

  const PHOTO_MAX_PX = 1024;
  const PHOTO_QUALITY = 0.8;
  const GRAM_STEP = 10;

  const num = (value) => {
    const n = parseFloat(String(value).replace(",", "."));
    return Number.isFinite(n) ? n : 0;
  };
  const fmtInt = (n) => Math.round(n).toLocaleString("de-AT");

  // --- capture page -------------------------------------------------------

  const capture = document.getElementById("meal-capture");
  if (capture) {
    const input = document.getElementById("photo-input");
    const data = document.getElementById("photo-data");
    const preview = document.getElementById("photo-preview");
    const remove = document.getElementById("photo-remove");
    const button = document.getElementById("photo-button");

    button.addEventListener("click", () => input.click());

    // Shrink in the browser: the model needs ~1024 px, and the photo is never stored.
    input.addEventListener("change", () => {
      const file = input.files && input.files[0];
      if (!file) return;
      const img = new Image();
      const url = URL.createObjectURL(file);
      img.onload = () => {
        const scale = Math.min(1, PHOTO_MAX_PX / Math.max(img.width, img.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
        const jpeg = canvas.toDataURL("image/jpeg", PHOTO_QUALITY);
        URL.revokeObjectURL(url);
        data.value = jpeg;
        preview.src = jpeg;
        preview.classList.remove("d-none");
        remove.classList.remove("d-none");
      };
      img.src = url;
    });

    remove.addEventListener("click", () => {
      data.value = "";
      input.value = "";
      preview.classList.add("d-none");
      remove.classList.add("d-none");
    });

    // The slot chosen at the top also applies to the "Schnell wieder" taps.
    capture.querySelectorAll("input[name=slot]").forEach((radio) =>
      radio.addEventListener("change", () =>
        document.querySelectorAll("input.quick-slot").forEach((hidden) => (hidden.value = radio.value))));

    capture.addEventListener("submit", (event) => {
      const manual = event.submitter && event.submitter.getAttribute("formmethod") === "get";
      if (manual) {
        data.disabled = true; // no photo in a GET URL
        return;
      }
      const text = document.getElementById("description").value.trim();
      if (!text && !data.value) {
        event.preventDefault();
        alert("Bitte einen Satz schreiben oder ein Foto aufnehmen");
        return;
      }
      const submit = document.getElementById("recognize-button");
      submit.disabled = true;
      submit.querySelector(".spinner-border").classList.remove("d-none");
      submit.querySelector(".label").textContent = "Erkenne …";
    });

    if (capture.dataset.openCamera === "true" && !button.disabled) {
      button.click();
    }
  }

  // --- review page --------------------------------------------------------

  const review = document.getElementById("meal-review");
  if (!review) return;

  const items = document.getElementById("meal-items");
  const template = document.getElementById("item-template");

  const itemValues = (row) => {
    const grams = num(row.querySelector(".item-grams").value);
    return {
      kcal: (num(row.querySelector(".item-kcal100").value) * grams) / 100,
      protein: (num(row.querySelector(".item-protein100").value) * grams) / 100,
    };
  };

  const recalc = () => {
    let kcal = 0;
    let protein = 0;
    items.querySelectorAll(".health-item").forEach((row) => {
      const v = itemValues(row);
      row.querySelector(".item-kcal").textContent = fmtInt(v.kcal);
      row.querySelector(".item-protein").textContent = fmtInt(v.protein);
      kcal += v.kcal;
      protein += v.protein;
    });
    document.getElementById("meal-kcal").textContent = fmtInt(kcal);
    document.getElementById("meal-protein").textContent = fmtInt(protein);
    const left = document.getElementById("meal-left");
    if (left) {
      const kcalLeft = num(review.dataset.kcalTarget) - num(review.dataset.kcalBefore) - kcal;
      const proteinLeft = Math.max(0, num(review.dataset.proteinTarget) - num(review.dataset.proteinBefore) - protein);
      document.getElementById("left-kcal").textContent = fmtInt(kcalLeft);
      document.getElementById("left-protein").textContent = fmtInt(proteinLeft);
    }
  };

  // Spring binds items[0..n]; keep the indexes gap-free after adding or removing rows.
  const renumber = () => {
    items.querySelectorAll(".health-item").forEach((row, index) => {
      row.dataset.index = index;
      row.querySelectorAll("[name^='items[']").forEach((field) => {
        field.name = field.name.replace(/^items\[\d+\]/, "items[" + index + "]");
        if (field.id) field.id = field.id.replace(/^items\d+/, "items" + index);
      });
    });
  };

  items.addEventListener("click", (event) => {
    const row = event.target.closest(".health-item");
    if (!row) return;
    const grams = row.querySelector(".item-grams");
    if (event.target.closest(".item-inc")) {
      grams.value = Math.round(num(grams.value)) + GRAM_STEP;
    } else if (event.target.closest(".item-dec")) {
      grams.value = Math.max(0, Math.round(num(grams.value)) - GRAM_STEP);
    } else if (event.target.closest(".item-remove")) {
      if (items.querySelectorAll(".health-item").length > 1) {
        row.remove();
        renumber();
      }
    } else {
      return;
    }
    recalc();
  });
  items.addEventListener("input", recalc);

  document.getElementById("item-add").addEventListener("click", () => {
    const index = items.querySelectorAll(".health-item").length;
    const html = template.innerHTML.replaceAll("__i__", String(index));
    items.insertAdjacentHTML("beforeend", html);
    items.lastElementChild.querySelector("input[type=text]").focus();
    recalc();
  });

  // The question's answer replaces one item's per-100 g values.
  const question = document.getElementById("meal-question");
  if (question) {
    const row = items.querySelectorAll(".health-item")[num(question.dataset.itemIndex)];
    question.querySelectorAll(".question-option").forEach((option) =>
      option.addEventListener("change", () => {
        if (!row || !option.checked) return;
        row.querySelector(".item-kcal100").value = option.dataset.kcal;
        row.querySelector(".item-protein100").value = option.dataset.protein;
        row.querySelector(".item-carbs100").value = option.dataset.carbs;
        row.querySelector(".item-fat100").value = option.dataset.fat;
        recalc();
      }));
  }

  recalc();
})();
