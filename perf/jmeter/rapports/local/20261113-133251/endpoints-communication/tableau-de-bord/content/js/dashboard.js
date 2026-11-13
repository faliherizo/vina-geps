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

    var data = {"OkPercent": 99.01719901719902, "KoPercent": 0.9828009828009828};
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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.9901719901719902, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "POST /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/non-lus"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/acheminement/unites"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/acheminement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/brouillons"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/acheminement/unites/{uniteId}"], "isController": false}, {"data": [0.4782608695652174, 500, 1500, "PUT /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/participants/{personId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}/destinataires"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/{id}/lectures"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/acheminement/unites/{uniteId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/archiver"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/raccrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/messages/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution/{id}/conversation"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes/{id}/membres"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/recherche"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels/en-cours"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/decrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/pieces-jointes/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/participants"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/refuser"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 1221, 12, 0.9828009828009828, 3.2596232596232584, 1, 56, 3.0, 5.0, 7.0, 12.0, 18.125408229915088, 22.767718801956534, 9.319877364022325], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["POST /api/communication/conversations", 35, 0, 0.0, 4.971428571428572, 3, 20, 4.0, 6.399999999999999, 11.199999999999953, 20.0, 0.7892659826361483, 0.6782754538279401, 0.45475286108918705], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/brouillon", 20, 0, 0.0, 3.3499999999999996, 2, 8, 3.0, 5.800000000000004, 7.899999999999999, 8.0, 2.1222410865874366, 1.0608096681345502, 1.1398755836162988], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 40, 0, 0.0, 5.374999999999999, 2, 12, 4.5, 8.0, 8.949999999999996, 12.0, 0.8538069115669491, 2.1115878940852526, 0.40772615210570134], "isController": false}, {"data": ["PUT /api/communication/conversations/{id}/brouillon", 33, 0, 0.0, 3.6969696969696977, 1, 7, 3.0, 6.0, 7.0, 7.0, 0.7615443195716891, 0.43296607089285305, 0.4305997666328203], "isController": false}, {"data": ["GET /api/communication/acheminement/unites", 40, 0, 0.0, 3.5750000000000006, 2, 9, 3.0, 5.0, 5.0, 9.0, 0.853406157325425, 5.612978974205799, 0.4058679673998848], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 40, 0, 0.0, 3.2749999999999995, 1, 8, 3.0, 4.899999999999999, 5.0, 8.0, 0.8539162735093824, 0.8534993222039579, 0.4428022863608223], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 40, 0, 0.0, 1.6999999999999997, 1, 3, 2.0, 2.0, 2.0, 3.0, 0.8539709649871904, 0.41447614218616563, 0.44032877882152005], "isController": false}, {"data": ["GET /api/communication/listes-distribution", 40, 0, 0.0, 2.1500000000000004, 1, 6, 2.0, 3.0, 3.9499999999999957, 6.0, 0.8540438978563498, 2.492907432317021, 0.40617126782816637], "isController": false}, {"data": ["GET /api/communication/brouillons", 40, 0, 0.0, 2.9499999999999997, 1, 51, 2.0, 2.0, 2.9499999999999957, 51.0, 0.8538251366120219, 0.4127377369364754, 0.3985629055669399], "isController": false}, {"data": ["PUT /api/communication/acheminement/unites/{uniteId}", 40, 0, 0.0, 5.1499999999999995, 2, 51, 4.0, 6.0, 11.749999999999979, 51.0, 0.854481756814492, 0.5499057399812014, 0.5006729043834914], "isController": false}, {"data": ["PUT /api/communication/listes-distribution/{id}", 23, 12, 52.17391304347826, 5.913043478260867, 1, 19, 5.0, 12.000000000000007, 17.999999999999986, 19.0, 0.6003027613927024, 0.37554606344939184, 0.3628783293965652], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/participants/{personId}", 20, 0, 0.0, 2.9, 2, 5, 3.0, 4.0, 4.949999999999999, 5.0, 2.1226915729144555, 1.0924399012948418, 1.1504822489917215], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/lu", 31, 0, 0.0, 5.032258064516128, 3, 7, 5.0, 6.0, 7.0, 7.0, 0.7321508703148248, 0.3617854886516615, 0.38824015877045887], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}/destinataires", 40, 0, 0.0, 1.9000000000000001, 1, 6, 2.0, 2.0, 3.0, 6.0, 0.8539162735093824, 0.47449058557308454, 0.44863960463676533], "isController": false}, {"data": ["GET /api/communication/messages/{id}/lectures", 40, 0, 0.0, 2.0750000000000006, 1, 4, 2.0, 3.0, 3.0, 4.0, 0.8538615890364172, 0.4356862112026641, 0.4352692865986424], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 40, 0, 0.0, 3.100000000000001, 1, 5, 3.0, 4.0, 4.949999999999996, 5.0, 0.8538615890364172, 1.2695562654762413, 0.4394385326388593], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 40, 0, 0.0, 1.7000000000000002, 1, 3, 2.0, 2.0, 3.0, 3.0, 0.8539345032236028, 1.0315595512574185, 0.4319707740916272], "isController": false}, {"data": ["GET /api/communication/groupes", 40, 0, 0.0, 1.7999999999999996, 1, 6, 2.0, 2.8999999999999986, 3.9499999999999957, 6.0, 0.8539345032236028, 2.5759801566969815, 0.3961121963195423], "isController": false}, {"data": ["DELETE /api/communication/listes-distribution/{id}", 20, 0, 0.0, 3.5499999999999994, 2, 8, 3.0, 5.0, 7.849999999999998, 8.0, 2.1231422505307855, 1.114131336252654, 1.1320660828025477], "isController": false}, {"data": ["DELETE /api/communication/acheminement/unites/{uniteId}", 20, 0, 0.0, 3.6, 2, 8, 3.0, 6.800000000000004, 7.949999999999999, 8.0, 2.1213406873143827, 1.1451925116673736, 1.0710284524819684], "isController": false}, {"data": ["POST /api/communication/listes-distribution", 25, 0, 0.0, 4.319999999999999, 3, 8, 4.0, 6.0, 7.399999999999999, 8.0, 0.6359866697194028, 0.44283056202142007, 0.37948032734233894], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/archiver", 20, 0, 0.0, 3.3, 2, 8, 3.0, 4.900000000000002, 7.849999999999998, 8.0, 2.1217907914279652, 1.3962585203161468, 1.1375616645448758], "isController": false}, {"data": ["POST /api/communication/appels/{id}/raccrocher", 20, 0, 0.0, 3.1, 2, 7, 3.0, 6.800000000000004, 7.0, 7.0, 2.120665889089174, 1.0893264234969782, 1.0969889857915387], "isController": false}, {"data": ["GET /api/communication/appels", 40, 0, 0.0, 1.7750000000000004, 1, 4, 2.0, 2.8999999999999986, 3.0, 4.0, 0.8536064874093043, 0.5184992530943235, 0.3951264404609475], "isController": false}, {"data": ["POST /api/communication/messages/{id}/lu", 20, 0, 0.0, 2.8000000000000007, 1, 8, 2.0, 4.0, 7.799999999999997, 8.0, 2.1208907741251326, 1.0438759278897136, 1.1142961293743372], "isController": false}, {"data": ["POST /api/communication/appels", 39, 0, 0.0, 5.076923076923076, 1, 50, 3.0, 7.0, 17.0, 50.0, 0.8410610308389045, 0.6135474512076774, 0.4271013047228812], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 56.0, 56, 56, 56.0, 56.0, 56.0, 56.0, 17.857142857142858, 36.9873046875, 5.876813616071429], "isController": false}, {"data": ["POST /api/communication/listes-distribution/{id}/conversation", 21, 0, 0.0, 3.095238095238095, 2, 5, 3.0, 4.0, 4.899999999999999, 5.0, 0.5619029780857839, 0.32343913052203466, 0.3232039590747331], "isController": false}, {"data": ["GET /api/communication/groupes/{id}/membres", 40, 0, 0.0, 1.8, 1, 6, 2.0, 2.0, 2.0, 6.0, 0.8539345032236028, 0.4127906045856283, 0.4094549229324111], "isController": false}, {"data": ["GET /api/communication/conversations", 40, 0, 0.0, 3.1499999999999995, 1, 9, 3.0, 4.0, 5.0, 9.0, 0.8538069115669491, 5.493880605776004, 0.40105578560908456], "isController": false}, {"data": ["GET /api/communication/messages/recherche", 40, 0, 0.0, 3.5999999999999996, 1, 6, 3.5, 5.0, 5.949999999999996, 6.0, 0.8539162735093824, 0.43863277330657724, 0.405276668872617], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 40, 0, 0.0, 2.850000000000001, 1, 51, 2.0, 2.0, 2.0, 51.0, 0.8537340191663287, 0.5185767186732974, 0.40268899536849295], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}", 40, 0, 0.0, 1.775, 1, 6, 2.0, 2.0, 2.0, 6.0, 0.8539162735093824, 0.5128501056721388, 0.4369649680848792], "isController": false}, {"data": ["POST /api/communication/appels/{id}/decrocher", 37, 0, 0.0, 3.027027027027027, 1, 14, 3.0, 4.0, 6.800000000000011, 14.0, 0.8159124988974156, 0.4191113031445709, 0.4212432535613478], "isController": false}, {"data": ["GET /api/communication/pieces-jointes/{id}", 40, 0, 0.0, 1.8250000000000002, 1, 5, 2.0, 3.0, 3.0, 5.0, 0.853898044573478, 0.4452944880881223, 0.4208408160596875], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/messages", 29, 0, 0.0, 5.620689655172413, 2, 11, 6.0, 10.0, 11.0, 11.0, 0.7018732755699695, 0.4539864586862869, 0.39548914062878165], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/participants", 27, 0, 0.0, 4.518518518518518, 2, 15, 4.0, 6.199999999999999, 11.799999999999983, 15.0, 0.6692279092824389, 0.6738027094435494, 0.39539344249597225], "isController": false}, {"data": ["POST /api/communication/appels/{id}/refuser", 20, 0, 0.0, 2.75, 2, 6, 3.0, 3.900000000000002, 5.899999999999999, 6.0, 2.122015915119363, 1.090019893899204, 1.0914704907161803], "isController": false}]}, function(index, item){
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
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": [{"data": ["500", 12, 100.0, 0.9828009828009828], "isController": false}]}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 1221, 12, "500", 12, "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": ["PUT /api/communication/listes-distribution/{id}", 23, 12, "500", 12, "", "", "", "", "", "", "", ""], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
