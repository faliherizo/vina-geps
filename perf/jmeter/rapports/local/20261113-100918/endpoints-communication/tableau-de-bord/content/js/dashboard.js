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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [1.0, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "POST /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/non-lus"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/acheminement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/brouillons"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/participants/{personId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}/destinataires"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/{id}/lectures"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/archiver"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/raccrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes/{id}/membres"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/recherche"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels/en-cours"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/decrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/pieces-jointes/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/refuser"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/participants"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 126, 0, 0.0, 3.944444444444445, 1, 56, 3.0, 7.0, 10.0, 45.20000000000016, 4.2116522378580745, 2.756813381605776, 2.0965883214393153], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["POST /api/communication/conversations", 4, 0, 0.0, 3.25, 2, 5, 3.0, 5.0, 5.0, 5.0, 0.6729475100942126, 0.3588177153432032, 0.3305591773216689], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/brouillon", 3, 0, 0.0, 4.333333333333333, 2, 7, 4.0, 7.0, 7.0, 7.0, 0.7513148009015778, 0.3756574004507889, 0.40353822314049587], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 5, 0, 0.0, 4.8, 2, 12, 3.0, 12.0, 12.0, 12.0, 0.6254691018263697, 0.6847176397923442, 0.29868592850888165], "isController": false}, {"data": ["PUT /api/communication/conversations/{id}/brouillon", 3, 0, 0.0, 2.6666666666666665, 2, 3, 3.0, 3.0, 3.0, 3.0, 0.7524454477050414, 0.3850404439428141, 0.4034106941309255], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 5, 0, 0.0, 2.6, 2, 4, 2.0, 4.0, 4.0, 4.0, 0.6268020559107433, 0.3709394979315532, 0.32503114422715307], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 5, 0, 0.0, 2.2, 2, 3, 2.0, 3.0, 3.0, 3.0, 0.6265664160401002, 0.3041049890350877, 0.32307330827067665], "isController": false}, {"data": ["GET /api/communication/listes-distribution", 5, 0, 0.0, 2.2, 2, 3, 2.0, 3.0, 3.0, 3.0, 0.6265664160401002, 0.6614436481829573, 0.2979861763784461], "isController": false}, {"data": ["GET /api/communication/brouillons", 5, 0, 0.0, 3.6, 1, 8, 2.0, 8.0, 8.0, 8.0, 0.6251562890722681, 0.3021995733308327, 0.29182100212553136], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/participants/{personId}", 1, 0, 0.0, 7.0, 7, 7, 7.0, 7.0, 7.0, 7.0, 142.85714285714286, 73.52120535714286, 77.42745535714286], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/lu", 2, 0, 0.0, 4.0, 3, 5, 4.0, 5.0, 5.0, 5.0, 1.0121457489878543, 0.5001423329959515, 0.5367140055668016], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}/destinataires", 5, 0, 0.0, 2.4, 2, 3, 2.0, 3.0, 3.0, 3.0, 0.6264879087833606, 0.3646355406590653, 0.32915087395063275], "isController": false}, {"data": ["GET /api/communication/messages/{id}/lectures", 5, 0, 0.0, 4.0, 2, 11, 2.0, 11.0, 11.0, 11.0, 0.6261740763932373, 0.30269157013149656, 0.31920201941139636], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 5, 0, 0.0, 2.8, 2, 4, 3.0, 4.0, 4.0, 4.0, 0.6265664160401002, 0.4491208489974937, 0.3224614270050125], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 5, 0, 0.0, 2.0, 1, 3, 2.0, 3.0, 3.0, 3.0, 0.62672348959639, 0.5300220136625721, 0.3170339527450489], "isController": false}, {"data": ["GET /api/communication/groupes", 5, 0, 0.0, 2.4, 2, 4, 2.0, 4.0, 4.0, 4.0, 0.6266449429753103, 0.41490749154029327, 0.290680027259055], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/archiver", 4, 0, 0.0, 4.25, 2, 6, 4.5, 6.0, 6.0, 6.0, 0.6729475100942126, 0.5213043089670256, 0.3607892412516824], "isController": false}, {"data": ["POST /api/communication/appels/{id}/raccrocher", 5, 0, 0.0, 5.6, 2, 10, 6.0, 10.0, 10.0, 10.0, 0.6287726358148893, 0.32298281878772633, 0.3254389618963783], "isController": false}, {"data": ["GET /api/communication/appels", 5, 0, 0.0, 4.6, 3, 7, 3.0, 7.0, 7.0, 7.0, 0.6248437890527369, 0.30204851130967253, 0.2892343320419895], "isController": false}, {"data": ["POST /api/communication/appels", 5, 0, 0.0, 8.2, 3, 16, 6.0, 16.0, 16.0, 16.0, 0.6285355122564426, 0.3154953645505971, 0.30444688874921433], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 56.0, 56, 56, 56.0, 56.0, 56.0, 56.0, 17.857142857142858, 36.9873046875, 5.876813616071429], "isController": false}, {"data": ["GET /api/communication/groupes/{id}/membres", 5, 0, 0.0, 2.2, 2, 3, 2.0, 3.0, 3.0, 3.0, 0.6264879087833606, 0.33404531073800275, 0.300396057824834], "isController": false}, {"data": ["GET /api/communication/conversations", 5, 0, 0.0, 2.2, 2, 3, 2.0, 3.0, 3.0, 3.0, 0.625312656328164, 0.984378908204102, 0.29372596454477234], "isController": false}, {"data": ["GET /api/communication/messages/recherche", 5, 0, 0.0, 3.2, 2, 5, 3.0, 5.0, 5.0, 5.0, 0.6266449429753103, 0.32188988281739567, 0.2974115647324226], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 5, 0, 0.0, 2.4, 2, 3, 2.0, 3.0, 3.0, 3.0, 0.6248437890527369, 0.30204851130967253, 0.2947261231567108], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}", 5, 0, 0.0, 3.0, 2, 6, 2.0, 6.0, 6.0, 6.0, 0.62625250501002, 0.4207634018036072, 0.3204651490480962], "isController": false}, {"data": ["POST /api/communication/appels/{id}/decrocher", 5, 0, 0.0, 5.0, 4, 7, 5.0, 7.0, 7.0, 7.0, 0.6284565108094521, 0.322820434263449, 0.324661615447461], "isController": false}, {"data": ["GET /api/communication/pieces-jointes/{id}", 5, 0, 0.0, 4.2, 2, 10, 3.0, 10.0, 10.0, 10.0, 0.6257039169065198, 0.32629481604304844, 0.30857468558378176], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/messages", 2, 0, 0.0, 3.0, 3, 3, 3.0, 3.0, 3.0, 3.0, 1.0136847440446022, 0.5068423720223011, 0.5434696528129751], "isController": false}, {"data": ["POST /api/communication/appels/{id}/refuser", 5, 0, 0.0, 3.4, 2, 5, 3.0, 5.0, 5.0, 5.0, 0.6285355122564426, 0.32286101508485227, 0.3234748192960402], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/participants", 1, 0, 0.0, 4.0, 4, 4, 4.0, 4.0, 4.0, 4.0, 250.0, 127.197265625, 135.009765625], "isController": false}]}, function(index, item){
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
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 126, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
