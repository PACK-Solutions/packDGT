// --- PDF.js worker ---
pdfjsLib.GlobalWorkerOptions.workerSrc =
    "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";

// --- State ---
var currentStep = 1;
var selectedTemplate = null;
var templatesMeta = [];
var currentDocumentId = null;

// --- PDF Viewer instances ---
var viewers = {};

function createViewer(containerId) {
    var container = document.getElementById(containerId);
    return {
        container: container,
        pagesEl: container.querySelector(".pdf-pages"),
        pageCurrentEl: container.querySelector(".page-current"),
        pageTotalEl: container.querySelector(".page-total"),
        zoomLevelEl: container.querySelector(".zoom-level"),
        pdfDoc: null,
        scale: 1.0,
        currentPage: 1,
        totalPages: 0,
        rendering: false,
        pageObserver: null
    };
}

function initViewers() {
    viewers.preview = createViewer("viewer-preview");
    viewers.final = createViewer("viewer-final");

    // Toolbar button handlers (delegation)
    document.addEventListener("click", function (e) {
        var btn = e.target.closest(".toolbar-btn");
        if (!btn) return;
        var viewerName = btn.dataset.viewer;
        var action = btn.dataset.action;
        if (!viewerName || !action) return;
        var v = viewers[viewerName];
        if (!v) return;

        switch (action) {
            case "prev-page": viewerGoToPage(v, v.currentPage - 1); break;
            case "next-page": viewerGoToPage(v, v.currentPage + 1); break;
            case "zoom-in": viewerSetScale(v, v.scale + 0.25); break;
            case "zoom-out": viewerSetScale(v, Math.max(0.25, v.scale - 0.25)); break;
            case "fit-width": viewerFitWidth(v); break;
        }
    });
}

async function viewerLoad(v, url) {
    v.pagesEl.innerHTML = '<p class="pdf-placeholder">Chargement du document...</p>';

    if (v.pdfDoc) {
        v.pdfDoc.destroy();
        v.pdfDoc = null;
    }

    try {
        v.pdfDoc = await pdfjsLib.getDocument(url).promise;
        v.totalPages = v.pdfDoc.numPages;
        v.currentPage = 1;
        v.pageTotalEl.textContent = v.totalPages;
        v.pageCurrentEl.textContent = "1";

        // Fit width on first load
        await viewerFitWidth(v);
    } catch (err) {
        v.pagesEl.innerHTML = '<p class="pdf-placeholder">Erreur de chargement du PDF</p>';
        console.error("PDF load error:", err);
    }
}

async function viewerRenderAllPages(v) {
    if (!v.pdfDoc || v.rendering) return;
    v.rendering = true;
    v.pagesEl.innerHTML = "";

    // Disconnect previous observer
    if (v.pageObserver) v.pageObserver.disconnect();

    for (var i = 1; i <= v.totalPages; i++) {
        var page = await v.pdfDoc.getPage(i);
        var viewport = page.getViewport({ scale: v.scale * window.devicePixelRatio });
        var displayViewport = page.getViewport({ scale: v.scale });

        var canvas = document.createElement("canvas");
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = displayViewport.width + "px";
        canvas.style.height = displayViewport.height + "px";
        canvas.dataset.page = i;

        var ctx = canvas.getContext("2d");
        await page.render({ canvasContext: ctx, viewport: viewport }).promise;

        v.pagesEl.appendChild(canvas);
    }

    // Observe visible pages for page indicator
    v.pageObserver = new IntersectionObserver(
        function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting && entry.intersectionRatio > 0.3) {
                    var pageNum = parseInt(entry.target.dataset.page);
                    if (pageNum && pageNum !== v.currentPage) {
                        v.currentPage = pageNum;
                        v.pageCurrentEl.textContent = pageNum;
                    }
                }
            });
        },
        { root: v.pagesEl, threshold: [0.3, 0.6] }
    );

    v.pagesEl.querySelectorAll("canvas").forEach(function (c) {
        v.pageObserver.observe(c);
    });

    v.rendering = false;
    updateZoomLabel(v);
}

function viewerGoToPage(v, pageNum) {
    if (pageNum < 1 || pageNum > v.totalPages) return;
    var canvas = v.pagesEl.querySelector('canvas[data-page="' + pageNum + '"]');
    if (canvas) {
        canvas.scrollIntoView({ behavior: "smooth", block: "start" });
        v.currentPage = pageNum;
        v.pageCurrentEl.textContent = pageNum;
    }
}

function viewerSetScale(v, newScale) {
    v.scale = Math.round(newScale * 100) / 100;
    updateZoomLabel(v);
    viewerRenderAllPages(v);
}

async function viewerFitWidth(v) {
    if (!v.pdfDoc) return;
    var page = await v.pdfDoc.getPage(1);
    var unscaledViewport = page.getViewport({ scale: 1.0 });
    var containerWidth = v.pagesEl.clientWidth - 32; // padding
    var fitScale = containerWidth / unscaledViewport.width;
    v.scale = Math.round(Math.min(fitScale, 2.0) * 100) / 100;
    updateZoomLabel(v);
    await viewerRenderAllPages(v);
}

function updateZoomLabel(v) {
    v.zoomLevelEl.textContent = Math.round(v.scale * 100) + "%";
}

// --- Sample data ---
var SAMPLE_DATA = {
    "attestation-assurance.docx": {
        data: {
            customerName: "Jean Dupont",
            policyNumber: "POL-2026-000123",
            startDate: "2026-04-01",
            endDate: "2027-03-31",
            premium: "1250,50 \u20ac",
            address: "10 rue Exemple, 75001 Paris"
        },
        tables: {
            movements: [
                ["01/04/2026", "Pr\u00e9l\u00e8vement prime mensuelle", "-125,05 \u20ac", "8 432,50 \u20ac"],
                ["15/03/2026", "Remboursement sinistre #S-2026-042", "+2 500,00 \u20ac", "8 557,55 \u20ac"],
                ["01/03/2026", "Pr\u00e9l\u00e8vement prime mensuelle", "-125,05 \u20ac", "6 057,55 \u20ac"],
                ["15/02/2026", "Avenant \u2014 extension garantie", "-45,00 \u20ac", "6 182,60 \u20ac"]
            ]
        }
    },
    "releve-compte-multi.docx": {
        data: {
            numeroAdherent: "7161694",
            identifiantClient: "905168641",
            numeroContrat: "AVA0001111",
            civilite: "M.",
            prenom: "Arnaud",
            nom: "Le Saint",
            adresseLigne1: "LIEU DIT LES ROBERTS",
            adresseLigne2: "05310 FREISSINIERES",
            dateReleve: "31 D\u00c9CEMBRE 2025",
            dateReleveCourt: "31/12/2025",
            dateEffetContrat: "19/01/2017",
            cadreFiscal: "Assurance-vie",
            dateTermeContrat: "19/01/2026",
            datePrecedente: "31/12/2024",
            datePrecedente3ans: "31/12/2022",
            datePrecedente5ans: "31/12/2020",
            dateCourante: "31/12/2025",
            anneeCourante: "2025",
            anneeProchaine: "2026",
            epargnePrecedente: "56 229.87 \u20ac",
            totalVersementsNets: "0.00 \u20ac",
            totalRachats: "56 000.00 \u20ac",
            totalArbitragesInvestis: "0.00 \u20ac",
            totalArbitragesDesinvestis: "0.00 \u20ac",
            interetsTechniquesInvestis: "300.45 \u20ac",
            cotisationPlancherEuros: "0.00 \u20ac",
            plusMoinsValueUCInvestie: "0.00 \u20ac",
            plusMoinsValueUCDesinvestie: "0.00 \u20ac",
            cotisationPlancherUC: "0.00 \u20ac",
            epargneCourante: "889.28 \u20ac",
            totalRachatsPartiels: "56 000.00 \u20ac",
            valeurRachat: "889.28 \u20ac",
            montantSupportEuros: "775.75 \u20ac",
            fraisSupportEuros: "-131.48",
            tauxMoyenRendement: "3.75 %",
            tauxRendementMinGaranti: "2.00 %",
            montantGarantiEuros: "776.51 \u20ac",
            dateGarantie: "19/01/2026",
            cumulVersementsBrutsPrecedent: "50 500.00 \u20ac",
            cumulVersementsBrutsCourant: "522.55 \u20ac",
            fraisEpargneGereeTaux: "0.70 %",
            fraisEpargneGereeMontant: "131.48 \u20ac",
            fraisEpargneGereeUCMontant: "32.37 \u20ac",
            fraisFinanciersUCMontant: "32.37 \u20ac",
            fraisArbitragesTaux: "0.00 %",
            fraisArbitragesMontant: "0.00 \u20ac",
            totalFraisPrelevesAnnee: "196.22 \u20ac",
            totalInteretsTechniquesInvestis: "494.34 \u20ac",
            totalInteretsTechniquesDesinvestis: "193.89 \u20ac",
            tauxRendementMinGarantiEuros: "0.00 %",
            montantRendementMinGaranti: "0.00 \u20ac",
            tauxParticipationExcedents: "0.00 %",
            montantParticipationExcedents: "494.34 \u20ac",
            tauxFraisEpargneGeree: "0.70 %",
            montantFraisEpargneGeree: "131.48 \u20ac",
            tauxPrelevementsSociaux: "17.20 %",
            montantPrelevementsSociaux: "62.41 \u20ac",
            montantCotisationPlancher: "0.00 \u20ac"
        },
        tables: {
            rachats: [
                ["Rachat partiel net", "14/05/2025", "22/05/2025", "", "", "56 000.00 \u20ac"]
            ],
            situationSupportsUC: [
                ["Choix Solidaire 1", "87.980 \u20ac", "1.2933468", "113.79 \u20ac", "-0.39"]
            ],
            evolutionUC: [
                ["Choix Solidaire", "21.22 %", "5.22 %", "2.12 %", "8.43 %"]
            ],
            evolutionSupportEuros: [
                ["Rendement minimum garanti", "31/12/2025", "31/12/2025", "0.00 %", "", "0.00 \u20ac", ""],
                ["Participation aux exc\u00e9dents", "31/12/2025", "31/12/2025", "0.00 %", "", "494.34 \u20ac", ""],
                ["Frais sur \u00e9pargne g\u00e9r\u00e9e", "31/12/2025", "31/12/2025", "0.70 %", "", "", "131.48 \u20ac"],
                ["Pr\u00e9l\u00e8vements sociaux", "31/12/2025", "31/12/2025", "17.20 %", "", "", "62.41 \u20ac"],
                ["Cotisation plancher", "31/12/2025", "31/12/2025", "", "", "", "0.00 \u20ac"]
            ],
            nbUCGaranti: [
                ["Choix Solidaire", "1.2933468", "0.90 %"]
            ],
            syntheseFrais: [
                ["Frais \u00e9pargne g\u00e9r\u00e9e euros", "0.70 %", "131.48 \u20ac"],
                ["Frais \u00e9pargne g\u00e9r\u00e9e UC", "Voir d\u00e9tail", "32.37 \u20ac"],
                ["Frais financiers UC", "Voir d\u00e9tail", "32.37 \u20ac"],
                ["Frais sur arbitrages", "0.00 %", "0.00 \u20ac"]
            ],
            detailFraisUC: [
                ["Choix Solidaire", "0.90 %", "32.37 \u20ac", "0.90 %", "32.37 \u20ac", "64.74 \u20ac"]
            ],
            actifsFinanciers: [
                ["FR0010177899", "Choix Solidaire / ECOFI", "3", "6.12 %", "", "0.90 %", "5.22 %", "", "0.90 %", "1.80 %", "4.32 %", ""]
            ]
        }
    }
};

// --- Init ---
document.addEventListener("DOMContentLoaded", init);

async function init() {
    initViewers();

    try {
        templatesMeta = await fetchTemplates();
        renderTemplateCards();
        showStep(1);

        document.getElementById("data-form").addEventListener("submit", function (e) {
            e.preventDefault();
            generatePdf();
        });
    } catch (err) {
        showError("Impossible de charger les templates : " + err.message);
    }
}

// --- API ---
async function apiFetch(url, options) {
    var resp = await fetch(url, options);
    if (!resp.ok) {
        var body = await resp.json().catch(function () { return {}; });
        throw new Error(body.message || "Erreur " + resp.status);
    }
    return resp;
}

async function fetchTemplates() {
    var resp = await apiFetch("/api/templates");
    var json = await resp.json();
    return json.templates;
}

// --- Steps ---
function showStep(n) {
    currentStep = n;
    document.querySelectorAll(".step-section").forEach(function (s) {
        s.classList.remove("active");
    });
    var section = document.getElementById("step-" + n);
    section.classList.add("active");

    document.querySelectorAll(".steps .step").forEach(function (s) {
        var stepNum = parseInt(s.dataset.step);
        s.classList.remove("active", "done");
        if (stepNum === n) s.classList.add("active");
        else if (stepNum < n) s.classList.add("done");
    });
}

// --- Step 1: Template cards ---
function renderTemplateCards() {
    var container = document.getElementById("template-cards");
    container.innerHTML = "";
    templatesMeta.forEach(function (tpl) {
        var card = document.createElement("div");
        card.className = "card";
        card.innerHTML =
            '<div class="card-icon">\uD83D\uDCC4</div>' +
            "<h3>" + escapeHtml(tpl.label) + "</h3>" +
            '<div class="meta">' +
                "<span>" + tpl.fields.length + " champs</span>" +
                "<span>" + tpl.tables.length + " tableau(x)</span>" +
            "</div>";
        card.addEventListener("click", function () {
            selectTemplate(tpl);
            document.querySelectorAll(".card").forEach(function (c) { c.classList.remove("selected"); });
            card.classList.add("selected");
        });
        container.appendChild(card);
    });
}

function selectTemplate(tpl) {
    selectedTemplate = tpl;
    document.getElementById("form-title").textContent = tpl.label;
    document.getElementById("form-container").classList.remove("hidden");
    renderForm(tpl);
    document.getElementById("form-container").scrollIntoView({ behavior: "smooth" });
}

// --- Step 1: Form ---
function renderForm(meta) {
    var fieldsContainer = document.getElementById("fields-container");
    var tablesContainer = document.getElementById("tables-container");
    fieldsContainer.innerHTML = "";
    tablesContainer.innerHTML = "";

    var groups = {};
    var ungrouped = [];
    meta.fields.forEach(function (f) {
        if (f.group) {
            if (!groups[f.group]) groups[f.group] = [];
            groups[f.group].push(f);
        } else {
            ungrouped.push(f);
        }
    });

    if (ungrouped.length > 0) {
        fieldsContainer.appendChild(createFieldsGrid(ungrouped));
    }

    Object.keys(groups).forEach(function (groupName, idx) {
        var details = document.createElement("details");
        details.className = "field-group";
        if (idx < 3) details.open = true;
        var summary = document.createElement("summary");
        summary.textContent = groupName + " (" + groups[groupName].length + ")";
        details.appendChild(summary);
        details.appendChild(createFieldsGrid(groups[groupName]));
        fieldsContainer.appendChild(details);
    });

    meta.tables.forEach(function (table) {
        tablesContainer.appendChild(createTableEditor(table));
    });
}

function createFieldsGrid(fields) {
    var grid = document.createElement("div");
    grid.className = "fields-grid";
    fields.forEach(function (f) {
        var div = document.createElement("div");
        div.className = "field";
        div.innerHTML =
            '<label for="field-' + f.key + '">' + escapeHtml(f.label) + "</label>" +
            '<input type="text" id="field-' + f.key + '" name="' + f.key + '" placeholder="' + escapeHtml(f.placeholder) + '">';
        grid.appendChild(div);
    });
    return grid;
}

function createTableEditor(tableDef) {
    var section = document.createElement("div");
    section.className = "table-section";
    section.dataset.tableKey = tableDef.key;

    var h3 = document.createElement("h3");
    h3.textContent = tableDef.label;
    section.appendChild(h3);

    var wrap = document.createElement("div");
    wrap.className = "table-editor-wrap";

    var table = document.createElement("table");
    table.className = "table-editor";

    var thead = document.createElement("thead");
    var headerRow = document.createElement("tr");
    tableDef.columns.forEach(function (col) {
        var th = document.createElement("th");
        th.textContent = col;
        headerRow.appendChild(th);
    });
    var thAction = document.createElement("th");
    thAction.style.width = "36px";
    headerRow.appendChild(thAction);
    thead.appendChild(headerRow);
    table.appendChild(thead);

    var tbody = document.createElement("tbody");
    tbody.dataset.tableKey = tableDef.key;
    table.appendChild(tbody);

    wrap.appendChild(table);
    section.appendChild(wrap);

    var actions = document.createElement("div");
    actions.className = "table-actions";
    var addBtn = document.createElement("button");
    addBtn.type = "button";
    addBtn.className = "btn btn-outline btn-sm";
    addBtn.textContent = "+ Ajouter une ligne";
    addBtn.addEventListener("click", function () {
        addTableRow(tbody, tableDef.columns.length);
    });
    actions.appendChild(addBtn);
    section.appendChild(actions);

    return section;
}

function addTableRow(tbody, colCount, values) {
    var tr = document.createElement("tr");
    for (var i = 0; i < colCount; i++) {
        var td = document.createElement("td");
        var input = document.createElement("input");
        input.type = "text";
        input.value = values && values[i] ? values[i] : "";
        td.appendChild(input);
        tr.appendChild(td);
    }
    var tdAction = document.createElement("td");
    var removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.className = "btn btn-danger btn-sm";
    removeBtn.textContent = "\u00d7";
    removeBtn.addEventListener("click", function () { tr.remove(); });
    tdAction.appendChild(removeBtn);
    tr.appendChild(tdAction);
    tbody.appendChild(tr);
}

// --- Pre-fill ---
function prefillSampleData() {
    if (!selectedTemplate) return;
    var sample = SAMPLE_DATA[selectedTemplate.name];
    if (!sample) return;

    if (sample.data) {
        Object.keys(sample.data).forEach(function (key) {
            var input = document.getElementById("field-" + key);
            if (input) input.value = sample.data[key];
        });
    }

    if (sample.tables) {
        Object.keys(sample.tables).forEach(function (tableKey) {
            var tbody = document.querySelector('tbody[data-table-key="' + tableKey + '"]');
            if (!tbody) return;
            var tableDef = selectedTemplate.tables.find(function (t) { return t.key === tableKey; });
            if (!tableDef) return;

            tbody.innerHTML = "";
            sample.tables[tableKey].forEach(function (row) {
                addTableRow(tbody, tableDef.columns.length, row);
            });
        });
    }
}

// --- Generate ---
async function generatePdf() {
    if (!selectedTemplate) return;

    var data = {};
    selectedTemplate.fields.forEach(function (f) {
        var input = document.getElementById("field-" + f.key);
        if (input && input.value) data[f.key] = input.value;
    });

    var tables = {};
    selectedTemplate.tables.forEach(function (tableDef) {
        var tbody = document.querySelector('tbody[data-table-key="' + tableDef.key + '"]');
        if (!tbody) return;
        var rows = [];
        tbody.querySelectorAll("tr").forEach(function (tr) {
            var cells = [];
            tr.querySelectorAll("input").forEach(function (input) {
                cells.push(input.value);
            });
            if (cells.length > 0) rows.push(cells);
        });
        if (rows.length > 0) tables[tableDef.key] = rows;
    });

    var request = {
        templateName: selectedTemplate.name,
        data: data,
        tables: tables
    };

    showLoading("G\u00e9n\u00e9ration du PDF en cours...");
    try {
        var resp = await apiFetch("/api/documents/generate", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(request)
        });
        var result = await resp.json();
        currentDocumentId = result.id;

        showStep(2);
        document.getElementById("free-text").value = "";

        // Load PDF into preview viewer
        await viewerLoad(viewers.preview, "/api/documents/" + result.id + "/pdf");
    } catch (err) {
        showError("Erreur lors de la g\u00e9n\u00e9ration : " + err.message);
    } finally {
        hideLoading();
    }
}

// --- Append text ---
async function appendText() {
    var text = document.getElementById("free-text").value.trim();
    if (!text) {
        showError("Veuillez saisir du texte avant de l'ajouter.");
        return;
    }
    if (!currentDocumentId) return;

    showLoading("Régénération du document avec votre texte...");
    try {
        await apiFetch("/api/documents/" + currentDocumentId + "/append-text", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ text: text })
        });

        showStep(3);
        showFinal();
    } catch (err) {
        showError("Erreur lors de l'ajout du texte : " + err.message);
    } finally {
        hideLoading();
    }
}

async function skipToFinal() {
    if (!currentDocumentId) return;
    showLoading("Finalisation du document...");
    try {
        await apiFetch("/api/documents/" + currentDocumentId + "/finalize", {
            method: "POST"
        });
        showStep(3);
        showFinal();
    } catch (err) {
        showError("Erreur lors de la finalisation : " + err.message);
    } finally {
        hideLoading();
    }
}

async function showFinal() {
    if (!currentDocumentId) return;
    var downloadLink = document.getElementById("download-link");
    downloadLink.href = "/api/documents/" + currentDocumentId + "/download";

    await viewerLoad(viewers.final, "/api/documents/" + currentDocumentId + "/pdf?t=" + Date.now());
}

function startOver() {
    currentDocumentId = null;
    selectedTemplate = null;
    document.getElementById("form-container").classList.add("hidden");
    document.querySelectorAll(".card").forEach(function (c) { c.classList.remove("selected"); });
    document.getElementById("free-text").value = "";

    // Clear viewers
    viewers.preview.pagesEl.innerHTML = "";
    viewers.final.pagesEl.innerHTML = "";

    showStep(1);
    window.scrollTo({ top: 0, behavior: "smooth" });
}

// --- Loading ---
function showLoading(msg) {
    document.getElementById("loading-message").textContent = msg || "Chargement...";
    document.getElementById("loading-overlay").classList.remove("hidden");
}

function hideLoading() {
    document.getElementById("loading-overlay").classList.add("hidden");
}

// --- Toast ---
function showError(message) {
    var toast = document.createElement("div");
    toast.className = "toast";
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(function () {
        toast.style.opacity = "0";
        toast.style.transform = "translateY(1rem)";
        toast.style.transition = "all 0.3s ease";
        setTimeout(function () { toast.remove(); }, 300);
    }, 4000);
}

// --- Utils ---
function escapeHtml(str) {
    var div = document.createElement("div");
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
}
