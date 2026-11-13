/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 100.0, "KoPercent": 0.0};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [1.0, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "GET /api/effectifs/presence/mon-profil"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/referentiels-profils"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/regles"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/remplacements"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/planification/postes"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/moi"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/remplacements/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/temps/export-paie"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/integrations/connecteurs"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/planification/quotas"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/regles/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/ressources/{id}/quarts"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/regles/faits"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/planification/periodes"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/configuration/paquet"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/absences/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/mon-horaire"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/milieux/{id}/code-qr"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/ma-ressource"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/perimetres/moi"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/profils"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/integrations/messages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/temps/feuilles"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/referentiels/{type}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/absences/{id}/impact"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/ressources/{id}/pointages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/absences/{id}/comblement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/pointages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/audit-localisations"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/audit-localisations.csv"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}/postes-disponibles"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/journal"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/liaisons/organisation"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/milieux"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/retention"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/referentiels/{type}/{code}/historique"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/planification/periodes/{id}/equipages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/planification/ressources"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/moi"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/temps/feuilles/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/periodes/{periodeId}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/places/{placeId}/candidats"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/regles/evaluations"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/remplacements/{id}/interesses"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/places/{placeId}/demandables"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/perimetres"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/referentiels"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}/disponibilites"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/pointages/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/absences"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/planification/periodes/{id}/comblement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/presence/moi"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 1087, 0, 0.0, 5.758969641214352, 1, 135, 4.0, 9.0, 14.0, 50.719999999999345, 17.419592634733416, 31.96342694327815, 8.352339480737488], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["GET /api/effectifs/presence/mon-profil", 20, 0, 0.0, 4.049999999999999, 1, 29, 3.0, 5.900000000000002, 27.849999999999984, 29.0, 2.189141856392294, 1.688888736865149, 1.0325737467162872], "isController": false}, {"data": ["GET /api/effectifs/presence/referentiels-profils", 20, 0, 0.0, 1.8499999999999999, 1, 4, 2.0, 3.0, 3.9499999999999993, 4.0, 2.1920210434020166, 1.254418292415607, 1.05533825624726], "isController": false}, {"data": ["GET /api/effectifs/regles", 16, 0, 0.0, 1.9374999999999998, 1, 4, 2.0, 3.3000000000000007, 4.0, 4.0, 2.2361984626135567, 2.240566037735849, 1.0263801537386443], "isController": false}, {"data": ["GET /api/effectifs/agph/remplacements", 20, 0, 0.0, 11.55, 4, 100, 6.0, 15.900000000000002, 95.79999999999994, 100.0, 2.1436227224008575, 1.0362238745980707, 1.009009914255091], "isController": false}, {"data": ["GET /api/effectifs/planification/postes", 20, 0, 0.0, 2.25, 1, 3, 2.0, 3.0, 3.0, 3.0, 2.1829294913774286, 3.4065637961143853, 1.0317752674088625], "isController": false}, {"data": ["GET /api/effectifs/echanges", 20, 0, 0.0, 4.65, 3, 8, 4.5, 7.700000000000006, 8.0, 8.0, 2.1605271686291454, 1.044395457491628, 0.9958679917899967], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}", 20, 0, 0.0, 9.050000000000002, 5, 36, 6.0, 19.200000000000017, 35.19999999999999, 36.0, 2.127659574468085, 1.6761552526595744, 1.0403507313829787], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi", 20, 0, 0.0, 9.599999999999998, 4, 56, 6.0, 14.800000000000004, 53.949999999999974, 56.0, 2.137208805300278, 1.037297633041248, 1.0846752110493694], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/moi", 20, 0, 0.0, 9.299999999999999, 4, 58, 6.5, 13.0, 55.74999999999997, 58.0, 2.1514629948364887, 2.0737245858433737, 1.0127003549913942], "isController": false}, {"data": ["GET /api/effectifs/agph/remplacements/{id}", 20, 0, 0.0, 12.950000000000003, 5, 94, 7.5, 22.10000000000002, 90.44999999999995, 94.0, 2.143852502947797, 1.2791932415049845, 1.0112116786365097], "isController": false}, {"data": ["GET /api/effectifs/temps/export-paie", 5, 0, 0.0, 2.2, 2, 3, 2.0, 3.0, 3.0, 3.0, 2.8801843317972353, 1.5385359663018434, 1.3528990855414746], "isController": false}, {"data": ["GET /api/effectifs/integrations/connecteurs", 20, 0, 0.0, 7.399999999999999, 2, 50, 5.0, 15.200000000000017, 48.299999999999976, 50.0, 2.166377816291161, 4.114848488951473, 1.0324144280762566], "isController": false}, {"data": ["GET /api/effectifs/planification/quotas", 20, 0, 0.0, 2.75, 2, 4, 3.0, 4.0, 4.0, 4.0, 2.1829294913774286, 2.7691654387688276, 1.0317752674088625], "isController": false}, {"data": ["GET /api/effectifs/regles/{id}", 10, 0, 0.0, 3.0000000000000004, 2, 4, 3.0, 4.0, 4.0, 4.0, 2.343566908835247, 1.9448858975861258, 1.0923676617061167], "isController": false}, {"data": ["GET /api/effectifs/echanges/ressources/{id}/quarts", 20, 0, 0.0, 4.1499999999999995, 3, 6, 4.0, 5.900000000000002, 6.0, 6.0, 2.1652051531882646, 1.6071996454476563, 1.0840826973043196], "isController": false}, {"data": ["GET /api/effectifs/regles/faits", 12, 0, 0.0, 1.5833333333333335, 1, 3, 1.5, 2.700000000000001, 3.0, 3.0, 2.282670724747955, 13.560044940079894, 1.0610852197070573], "isController": false}, {"data": ["GET /api/effectifs/planification/periodes", 20, 0, 0.0, 2.4000000000000004, 1, 6, 2.0, 3.900000000000002, 5.899999999999999, 6.0, 2.1836445026749645, 2.6485219456272517, 1.0363781526367506], "isController": false}, {"data": ["GET /api/effectifs/configuration/paquet", 20, 0, 0.0, 15.650000000000002, 6, 79, 9.5, 34.7, 76.79999999999997, 79.0, 2.1443122118580464, 9.37927187198456, 1.0135225688860299], "isController": false}, {"data": ["GET /api/effectifs/absences/{id}", 20, 0, 0.0, 11.949999999999998, 5, 70, 8.0, 16.800000000000004, 67.34999999999997, 70.0, 2.1072595090085344, 1.4454483194605416, 0.9863373854177643], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 135.0, 135, 135, 135.0, 135.0, 135.0, 135.0, 7.407407407407407, 15.342881944444443, 2.4377893518518516], "isController": false}, {"data": ["GET /api/effectifs/mon-horaire", 20, 0, 0.0, 2.4, 2, 3, 2.0, 3.0, 3.0, 3.0, 2.1836445026749645, 2.268943116060705, 1.012921033955672], "isController": false}, {"data": ["GET /api/effectifs/presence/milieux/{id}/code-qr", 20, 0, 0.0, 4.1499999999999995, 2, 29, 3.0, 5.800000000000004, 27.849999999999984, 29.0, 2.1872265966754156, 1.2025474354768153, 1.0908365458223972], "isController": false}, {"data": ["GET /api/effectifs/ma-ressource", 20, 0, 0.0, 3.75, 2, 21, 3.0, 5.800000000000004, 20.24999999999999, 21.0, 2.1817388458601505, 1.6853080342533, 1.0141676666303043], "isController": false}, {"data": ["GET /api/effectifs/echanges/{id}", 20, 0, 0.0, 4.6, 3, 8, 4.0, 6.900000000000002, 7.949999999999999, 8.0, 2.165674066053059, 1.2710645641580942, 1.0003553059014618], "isController": false}, {"data": ["GET /api/effectifs/perimetres/moi", 20, 0, 0.0, 3.0, 1, 5, 3.0, 4.0, 4.949999999999999, 5.0, 2.1831677764436197, 1.3666118600589456, 1.0190958956445804], "isController": false}, {"data": ["GET /api/effectifs/presence/profils", 20, 0, 0.0, 1.9999999999999998, 1, 4, 2.0, 3.0, 3.9499999999999993, 4.0, 2.1910604732690624, 2.6468181693689745, 1.0270595968448728], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique", 20, 0, 0.0, 6.3500000000000005, 4, 13, 5.0, 12.0, 12.95, 13.0, 2.1537798836958864, 1.259246109735085, 1.1225315663364204], "isController": false}, {"data": ["GET /api/effectifs/integrations/messages", 20, 0, 0.0, 4.6499999999999995, 2, 27, 3.0, 7.0, 25.999999999999986, 27.0, 2.170609941393532, 1.0492694540915997, 1.0280720913826784], "isController": false}, {"data": ["GET /api/effectifs/temps/feuilles", 3, 0, 0.0, 3.0, 2, 5, 2.0, 5.0, 5.0, 5.0, 4.037685060565276, 5.153568724764469, 1.8847787685060566], "isController": false}, {"data": ["GET /api/effectifs/referentiels/{type}", 20, 0, 0.0, 2.1500000000000004, 1, 3, 2.0, 3.0, 3.0, 3.0, 2.1922613175490517, 2.4170537378055466, 1.034045133179875], "isController": false}, {"data": ["GET /api/effectifs/absences/{id}/impact", 20, 0, 0.0, 7.45, 4, 16, 6.0, 15.0, 15.95, 16.0, 2.1229168878038425, 1.1632506501432969, 1.0067269928882285], "isController": false}, {"data": ["GET /api/effectifs/presence/ressources/{id}/pointages", 20, 0, 0.0, 2.9, 2, 8, 2.5, 4.0, 7.799999999999997, 8.0, 2.1920210434020166, 1.4642015563349406, 1.1039309102367383], "isController": false}, {"data": ["GET /api/effectifs/absences/{id}/comblement", 20, 0, 0.0, 8.2, 4, 15, 7.0, 13.800000000000004, 14.95, 15.0, 2.1233676611105214, 1.2041402351629684, 1.015235162968468], "isController": false}, {"data": ["GET /api/effectifs/presence/pointages", 20, 0, 0.0, 3.1999999999999997, 2, 12, 2.0, 6.800000000000004, 11.749999999999996, 12.0, 2.190340597962983, 2.406380051473004, 1.0310001642755449], "isController": false}, {"data": ["GET /api/effectifs/presence/audit-localisations", 20, 0, 0.0, 4.65, 2, 31, 3.0, 9.50000000000001, 29.949999999999985, 31.0, 2.1845985800109227, 2.568610049153468, 1.0496313489896232], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes", 20, 0, 0.0, 8.7, 4, 25, 6.5, 16.900000000000002, 24.599999999999994, 25.0, 2.1233676611105214, 2.699828803482323, 0.9911813886824503], "isController": false}, {"data": ["GET /api/effectifs/presence/audit-localisations.csv", 20, 0, 0.0, 5.3, 2, 48, 3.0, 7.6000000000000085, 45.99999999999997, 48.0, 2.1860312602470215, 1.7014325336102307, 1.058858891682151], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}/postes-disponibles", 20, 0, 0.0, 9.6, 5, 32, 8.5, 16.50000000000001, 31.24999999999999, 32.0, 2.139495079161318, 1.7189087906504064, 1.0858355396876336], "isController": false}, {"data": ["GET /api/effectifs/journal", 20, 0, 0.0, 8.2, 2, 58, 4.0, 17.300000000000015, 55.99999999999997, 58.0, 2.1713169037020954, 76.77640864184127, 0.998720958636413], "isController": false}, {"data": ["GET /api/effectifs/liaisons/organisation", 20, 0, 0.0, 6.199999999999999, 2, 36, 3.0, 25.10000000000004, 35.55, 36.0, 2.1746221593998043, 1.3187894150266393, 1.0299724094813527], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}", 20, 0, 0.0, 5.3500000000000005, 3, 11, 5.0, 8.900000000000002, 10.899999999999999, 11.0, 2.15633423180593, 1.0465801886792452, 1.1442890835579513], "isController": false}, {"data": ["GET /api/effectifs/milieux", 20, 0, 0.0, 2.6500000000000004, 1, 6, 2.0, 4.900000000000002, 5.949999999999999, 6.0, 2.182453077258839, 2.448865806416412, 1.0038431634657354], "isController": false}, {"data": ["GET /api/effectifs/retention", 7, 0, 0.0, 2.2857142857142856, 1, 6, 2.0, 6.0, 6.0, 6.0, 2.555677254472435, 2.633046390105878, 1.180503263052209], "isController": false}, {"data": ["GET /api/effectifs/referentiels/{type}/{code}/historique", 18, 0, 0.0, 2.1666666666666665, 1, 5, 2.0, 3.200000000000003, 5.0, 5.0, 2.2178413011335634, 1.5290976158206013, 1.0872620441103993], "isController": false}, {"data": ["GET /api/effectifs/planification/periodes/{id}/equipages", 20, 0, 0.0, 4.200000000000001, 3, 7, 4.0, 5.900000000000002, 6.949999999999999, 7.0, 2.1826912583215106, 14.803293476481501, 1.105626909854851], "isController": false}, {"data": ["GET /api/effectifs/planification/ressources", 20, 0, 0.0, 2.7, 1, 6, 2.0, 4.900000000000002, 5.949999999999999, 6.0, 2.1841214371519055, 2.873058179534782, 1.040870372392705], "isController": false}, {"data": ["GET /api/effectifs/echanges/moi", 20, 0, 0.0, 4.9, 3, 11, 4.0, 8.800000000000004, 10.899999999999999, 11.0, 2.161227577263886, 2.2456505295007565, 1.0046331316187596], "isController": false}, {"data": ["GET /api/effectifs/temps/feuilles/{id}", 1, 0, 0.0, 4.0, 4, 4, 4.0, 4.0, 4.0, 4.0, 250.0, 427.490234375, 122.314453125], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/periodes/{periodeId}", 20, 0, 0.0, 9.6, 3, 57, 7.0, 13.800000000000004, 54.849999999999966, 57.0, 2.153084293250081, 1.2630446630423082, 1.0717061174507483], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/places/{placeId}/candidats", 20, 0, 0.0, 6.45, 4, 21, 5.0, 10.0, 20.449999999999992, 21.0, 2.156566745740781, 1.3607009515850768, 1.090287699482424], "isController": false}, {"data": ["GET /api/effectifs/regles/evaluations", 14, 0, 0.0, 2.7857142857142856, 2, 4, 3.0, 4.0, 4.0, 4.0, 2.2909507445589923, 8.038462915234822, 1.0783576746849943], "isController": false}, {"data": ["GET /api/effectifs/agph/remplacements/{id}/interesses", 20, 0, 0.0, 12.100000000000001, 4, 104, 6.5, 15.50000000000001, 99.59999999999994, 104.0, 2.143852502947797, 1.151483277950477, 1.0321477382356092], "isController": false}, {"data": ["GET /api/effectifs/echanges/places/{placeId}/demandables", 20, 0, 0.0, 5.199999999999999, 3, 13, 5.0, 7.800000000000004, 12.749999999999996, 13.0, 2.163097555699762, 1.0456379785853342, 1.0851398577763356], "isController": false}, {"data": ["GET /api/effectifs/perimetres", 20, 0, 0.0, 2.85, 2, 6, 3.0, 3.900000000000002, 5.899999999999999, 6.0, 2.1834061135371177, 1.0554551037117903, 1.0106782205240175], "isController": false}, {"data": ["GET /api/effectifs/referentiels", 20, 0, 0.0, 1.7999999999999998, 1, 3, 2.0, 2.900000000000002, 3.0, 3.0, 2.1922613175490517, 7.013523512002632, 1.019058971829442], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}/disponibilites", 20, 0, 0.0, 9.4, 4, 35, 6.5, 22.10000000000002, 34.39999999999999, 35.0, 2.130606157451795, 1.3393273276872268, 1.0730015580057526], "isController": false}, {"data": ["GET /api/effectifs/presence/pointages/{id}", 20, 0, 0.0, 3.3999999999999995, 2, 6, 3.0, 4.900000000000002, 5.949999999999999, 6.0, 2.190100744634253, 1.6430033125273764, 1.0465002874507228], "isController": false}, {"data": ["GET /api/effectifs/absences", 20, 0, 0.0, 9.4, 6, 28, 7.5, 16.800000000000004, 27.449999999999992, 28.0, 2.1059281878487943, 1.9352328366852691, 0.9707012740865537], "isController": false}, {"data": ["GET /api/effectifs/planification/periodes/{id}/comblement", 20, 0, 0.0, 5.050000000000001, 3, 9, 5.0, 7.900000000000002, 8.95, 9.0, 2.1831677764436197, 7.125782870319834, 1.1080002865407708], "isController": false}, {"data": ["GET /api/effectifs/presence/moi", 20, 0, 0.0, 4.299999999999999, 2, 13, 4.0, 5.900000000000002, 12.649999999999995, 13.0, 2.1872265966754156, 4.487659257436571, 1.0167186132983377], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": []}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 1087, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
