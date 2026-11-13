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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [1.0, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "POST /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/non-lus"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/acheminement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/brouillons"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/participants/{personId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}/destinataires"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/{id}/lectures"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/archiver"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/raccrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/messages/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution/{id}/conversation"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes/{id}/membres"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/recherche"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels/en-cours"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/decrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/pieces-jointes/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/refuser"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/participants"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 9257, 0, 0.0, 1.812142162687701, 0, 76, 2.0, 3.0, 3.0, 4.0, 76.53006390595161, 43.32601045343877, 38.83069366479551], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["POST /api/communication/conversations", 272, 0, 0.0, 1.7316176470588232, 1, 4, 2.0, 2.0, 3.0, 4.0, 2.28373760526603, 1.217696027807864, 1.1217968900867317], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/brouillon", 272, 0, 0.0, 2.1176470588235303, 1, 5, 2.0, 3.0, 3.0, 4.0, 2.283833481670557, 1.1419167408352786, 1.2266683739441469], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 273, 0, 0.0, 1.7435897435897436, 1, 4, 2.0, 2.0, 2.3000000000000114, 3.0, 2.280149337253297, 1.1667951686725857, 1.0888603768719358], "isController": false}, {"data": ["PUT /api/communication/conversations/{id}/brouillon", 272, 0, 0.0, 2.0367647058823533, 1, 4, 2.0, 3.0, 3.0, 3.269999999999982, 2.2838910113774715, 1.1687098534783156, 1.2244689113732734], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 273, 0, 0.0, 1.410256410256409, 0, 4, 1.0, 2.0, 2.0, 3.0, 2.280301701455885, 1.3494754209787756, 1.1824611362041746], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 273, 0, 0.0, 1.355311355311356, 0, 3, 1.0, 2.0, 2.0, 2.259999999999991, 2.2802826548169923, 1.1067387494570755, 1.175770743890012], "isController": false}, {"data": ["GET /api/communication/listes-distribution", 272, 0, 0.0, 1.2316176470588236, 0, 11, 1.0, 2.0, 2.0, 3.0, 2.28233872591797, 1.1128826798180842, 1.0854482026582533], "isController": false}, {"data": ["GET /api/communication/brouillons", 273, 0, 0.0, 1.1721611721611715, 0, 15, 1.0, 2.0, 2.0, 2.519999999999982, 2.279863709246392, 1.1020825547626603, 1.0642332549021245], "isController": false}, {"data": ["PUT /api/communication/listes-distribution/{id}", 272, 0, 0.0, 2.0257352941176476, 1, 20, 2.0, 3.0, 3.0, 7.809999999999945, 2.2839485439828033, 1.208886826990898, 1.2155780824908475], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/participants/{personId}", 272, 0, 0.0, 2.3235294117647043, 1, 6, 2.0, 3.0, 3.0, 6.0, 2.2838910113774715, 1.1754009404257106, 1.2378510852680633], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/lu", 272, 0, 0.0, 2.6213235294117654, 1, 6, 3.0, 3.0, 4.0, 4.269999999999982, 2.2838910113774715, 1.128563331793946, 1.2110867374784835], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}/destinataires", 272, 0, 0.0, 1.5404411764705885, 0, 19, 1.0, 2.0, 2.0, 3.0, 2.282472790742559, 1.2094332680898556, 1.1991898060737272], "isController": false}, {"data": ["GET /api/communication/messages/{id}/lectures", 272, 0, 0.0, 1.7095588235294124, 1, 5, 2.0, 2.0, 2.0, 3.269999999999982, 2.2825302519174935, 1.2246162259998659, 1.1635554604501284], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 273, 0, 0.0, 1.8095238095238089, 1, 4, 2.0, 2.0, 2.0, 3.259999999999991, 2.2802255168093546, 1.6344585247442056, 1.1735144993735644], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 273, 0, 0.0, 1.4065934065934067, 1, 4, 1.0, 2.0, 2.0, 3.0, 2.2802445624937353, 1.9773995815375363, 1.1534830892302295], "isController": false}, {"data": ["GET /api/communication/groupes", 273, 0, 0.0, 1.2344322344322347, 0, 4, 1.0, 2.0, 2.0, 3.0, 2.280358843281712, 1.5098469685009772, 1.057783643123841], "isController": false}, {"data": ["DELETE /api/communication/listes-distribution/{id}", 272, 0, 0.0, 2.4742647058823524, 1, 17, 2.0, 3.0, 3.349999999999966, 7.9699999999998, 2.2839485439828033, 1.2081242206655358, 1.2178085009908306], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/archiver", 272, 0, 0.0, 2.338235294117647, 1, 5, 2.0, 3.0, 3.0, 4.0, 2.28381430574564, 1.1352162906489558, 1.224427786967145], "isController": false}, {"data": ["POST /api/communication/listes-distribution", 272, 0, 0.0, 2.1213235294117663, 1, 19, 2.0, 3.0, 3.0, 5.079999999999927, 2.283910188590525, 1.0928867113372631, 1.1352639511646263], "isController": false}, {"data": ["POST /api/communication/appels/{id}/raccrocher", 272, 0, 0.0, 1.9522058823529407, 1, 25, 2.0, 2.700000000000017, 3.0, 4.0, 2.283718430951102, 1.1730819283987104, 1.1820027035196132], "isController": false}, {"data": ["GET /api/communication/appels", 273, 0, 0.0, 1.3223443223443232, 0, 3, 1.0, 2.0, 2.0, 3.0, 2.2798827488872, 1.1020917584952774, 1.0553363505591142], "isController": false}, {"data": ["POST /api/communication/messages/{id}/lu", 271, 0, 0.0, 1.896678966789668, 1, 29, 2.0, 2.8000000000000114, 3.0, 4.279999999999973, 2.2813944286832735, 1.1228738203675486, 1.198623244757423], "isController": false}, {"data": ["POST /api/communication/appels", 272, 0, 0.0, 2.1102941176470575, 1, 58, 2.0, 2.0, 3.0, 9.049999999999727, 2.2836225642058956, 1.1462714824236624, 1.1061296795372306], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 76.0, 76, 76, 76.0, 76.0, 76.0, 76.0, 13.157894736842104, 27.25380345394737, 4.330283717105263], "isController": false}, {"data": ["POST /api/communication/listes-distribution/{id}/conversation", 271, 0, 0.0, 2.0479704797047966, 1, 5, 2.0, 3.0, 3.0, 5.0, 2.2813560178130974, 1.2075146109909167, 1.245388685505392], "isController": false}, {"data": ["GET /api/communication/groupes/{id}/membres", 272, 0, 0.0, 1.3676470588235288, 0, 3, 1.0, 2.0, 2.0, 3.0, 2.2823578770715334, 1.2169603524229076, 1.0943727711348856], "isController": false}, {"data": ["GET /api/communication/conversations", 273, 0, 0.0, 1.4652014652014653, 1, 9, 1.0, 2.0, 2.0, 2.259999999999991, 2.279996993410558, 3.736166948186442, 1.0709751502250766], "isController": false}, {"data": ["GET /api/communication/messages/recherche", 272, 0, 0.0, 1.7242647058823524, 1, 4, 2.0, 2.0, 3.0, 3.0, 2.28249194414608, 1.1724519166219118, 1.0832920750537056], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 273, 0, 0.0, 1.208791208791209, 0, 7, 1.0, 2.0, 2.0, 2.259999999999991, 2.2799208291228568, 1.1021101664216935, 1.07539234420541], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}", 272, 0, 0.0, 1.7904411764705894, 1, 63, 1.0, 2.0, 2.0, 11.539999999999964, 2.28243448489985, 1.2116746891022143, 1.1679645215698449], "isController": false}, {"data": ["POST /api/communication/appels/{id}/decrocher", 272, 0, 0.0, 2.3161764705882355, 1, 7, 2.0, 3.0, 3.0, 5.0, 2.283603391822685, 1.1730228360339183, 1.1797130803458988], "isController": false}, {"data": ["GET /api/communication/pieces-jointes/{id}", 272, 0, 0.0, 1.422794117647059, 0, 8, 1.0, 2.0, 2.0, 3.269999999999982, 2.2825877159856667, 1.190333828453463, 1.1256902310280876], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/messages", 272, 0, 0.0, 2.0036764705882355, 1, 4, 2.0, 3.0, 3.0, 3.269999999999982, 2.2839485439828033, 1.1419742719914017, 1.2244997564907802], "isController": false}, {"data": ["POST /api/communication/appels/{id}/refuser", 272, 0, 0.0, 1.8823529411764701, 1, 7, 2.0, 2.0, 3.0, 3.269999999999982, 2.2836800832871558, 1.1730622302822695, 1.1752923866136047], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/participants", 272, 0, 0.0, 2.441176470588237, 1, 13, 2.0, 3.0, 3.0, 4.269999999999982, 2.2839485439828033, 1.1620480384912504, 1.2334214304907132], "isController": false}]}, function(index, item){
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
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 9257, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
