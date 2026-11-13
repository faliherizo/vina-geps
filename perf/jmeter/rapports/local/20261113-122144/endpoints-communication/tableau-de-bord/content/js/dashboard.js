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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [1.0, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "POST /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/non-lus"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/acheminement/unites"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/acheminement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/brouillons"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/acheminement/unites/{uniteId}"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/participants/{personId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}/destinataires"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/{id}/lectures"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/acheminement/unites/{uniteId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/archiver"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/raccrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/messages/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution/{id}/conversation"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes/{id}/membres"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/recherche"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels/en-cours"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/decrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/pieces-jointes/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/refuser"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/participants"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 2271, 0, 0.0, 2.8450022016732697, 0, 60, 2.0, 5.7999999999999545, 7.0, 13.0, 18.861342967484738, 21.49027144065861, 9.780183612080062], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["POST /api/communication/conversations", 60, 0, 0.0, 4.000000000000001, 2, 9, 4.0, 6.0, 7.0, 9.0, 0.7129531708592274, 0.6126941312071486, 0.41078356524115645], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/brouillon", 60, 0, 0.0, 3.3, 1, 15, 3.0, 5.0, 6.949999999999996, 15.0, 0.7130717944451707, 0.3565358972225854, 0.3829975458445741], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 64, 0, 0.0, 7.624999999999999, 2, 22, 7.0, 13.0, 15.0, 22.0, 0.5635191772620012, 2.508802235586236, 0.2691024196104674], "isController": false}, {"data": ["PUT /api/communication/conversations/{id}/brouillon", 60, 0, 0.0, 2.816666666666667, 1, 14, 2.0, 4.899999999999999, 6.0, 14.0, 0.7130717944451707, 0.3648922073137397, 0.4031919618981971], "isController": false}, {"data": ["GET /api/communication/acheminement/unites", 74, 0, 0.0, 1.9324324324324325, 1, 7, 2.0, 3.0, 3.0, 7.0, 0.6238618736089566, 1.241236163333783, 0.2966999340308222], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 60, 0, 0.0, 1.8, 1, 4, 2.0, 3.0, 3.0, 4.0, 0.7125552230297848, 0.4216879542539547, 0.36949885100470287], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 60, 0, 0.0, 1.4833333333333334, 1, 3, 1.0, 2.0, 2.0, 3.0, 0.7125721479299779, 0.34584800539179594, 0.3674200137763949], "isController": false}, {"data": ["GET /api/communication/listes-distribution", 60, 0, 0.0, 2.4000000000000004, 1, 4, 2.0, 3.0, 4.0, 4.0, 0.7125298371869322, 3.5445576080373367, 0.33886917061526955], "isController": false}, {"data": ["GET /api/communication/brouillons", 68, 0, 0.0, 1.2647058823529416, 0, 4, 1.0, 2.0, 2.0, 4.0, 0.5883828988240994, 0.28442337394329026, 0.2746552984745308], "isController": false}, {"data": ["PUT /api/communication/acheminement/unites/{uniteId}", 60, 0, 0.0, 4.283333333333331, 2, 14, 4.0, 7.0, 8.949999999999996, 14.0, 0.712809180982251, 0.4587316897141635, 0.4176616294817877], "isController": false}, {"data": ["PUT /api/communication/listes-distribution/{id}", 60, 0, 0.0, 3.0, 1, 13, 2.0, 5.899999999999999, 11.699999999999974, 13.0, 0.7128176493649984, 0.3772921542537393, 0.43089270015325576], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/participants/{personId}", 60, 0, 0.0, 2.9166666666666665, 1, 7, 3.0, 4.0, 5.0, 7.0, 0.7131226452095986, 0.36700745510298677, 0.3865069024329368], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/lu", 60, 0, 0.0, 6.35, 3, 10, 6.0, 9.0, 10.0, 10.0, 0.713029424347578, 0.35233680539050244, 0.37810056388743646], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}/destinataires", 60, 0, 0.0, 1.6166666666666663, 1, 7, 1.5, 2.0, 2.9499999999999957, 7.0, 0.7126144636982314, 0.3897110348349703, 0.37440095846645366], "isController": false}, {"data": ["GET /api/communication/messages/{id}/lectures", 60, 0, 0.0, 1.9000000000000001, 1, 8, 2.0, 2.0, 3.0, 8.0, 0.7126060001425212, 0.3699891179125395, 0.3632620430414024], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 60, 0, 0.0, 3.75, 2, 6, 4.0, 6.0, 6.0, 6.0, 0.7125721479299779, 0.5107694888482459, 0.366724142538182], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 62, 0, 0.0, 1.4516129032258067, 0, 3, 1.0, 2.0, 2.0, 3.0, 0.550905439746939, 0.47392029197988306, 0.27868068143448665], "isController": false}, {"data": ["GET /api/communication/groupes", 60, 0, 0.0, 1.2666666666666668, 0, 2, 1.0, 2.0, 2.0, 2.0, 0.7125298371869322, 0.4717726851686915, 0.33051921158573516], "isController": false}, {"data": ["DELETE /api/communication/listes-distribution/{id}", 60, 0, 0.0, 2.516666666666666, 1, 6, 2.0, 4.0, 4.949999999999996, 6.0, 0.7130548458018896, 0.3763383705211243, 0.38020307207796067], "isController": false}, {"data": ["DELETE /api/communication/acheminement/unites/{uniteId}", 60, 0, 0.0, 3.6833333333333345, 2, 23, 3.0, 4.0, 7.949999999999996, 23.0, 0.7128515249082203, 0.35809650821561384, 0.35990648279057613], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/archiver", 60, 0, 0.0, 2.6833333333333336, 1, 6, 2.0, 4.899999999999999, 6.0, 6.0, 0.7130463717823783, 0.3676297187329166, 0.3822875567466071], "isController": false}, {"data": ["POST /api/communication/listes-distribution", 60, 0, 0.0, 4.499999999999999, 2, 9, 4.0, 7.0, 7.949999999999996, 9.0, 0.7130463717823783, 0.4964863897273786, 0.4254602862881183], "isController": false}, {"data": ["POST /api/communication/appels/{id}/raccrocher", 60, 0, 0.0, 3.333333333333334, 1, 28, 2.0, 6.0, 11.749999999999979, 28.0, 0.7129192856548757, 0.36620658618600066, 0.36875935706562424], "isController": false}, {"data": ["GET /api/communication/appels", 72, 0, 0.0, 1.5000000000000002, 0, 3, 1.0, 2.0, 3.0, 3.0, 0.6122553104644637, 0.4818122438306774, 0.28340724332046463], "isController": false}, {"data": ["POST /api/communication/messages/{id}/lu", 56, 0, 0.0, 2.0000000000000004, 1, 4, 2.0, 3.0, 4.0, 4.0, 0.6816798539257456, 0.33551430310407787, 0.3581482045039562], "isController": false}, {"data": ["POST /api/communication/appels", 60, 0, 0.0, 3.650000000000001, 1, 13, 3.0, 7.0, 10.849999999999987, 13.0, 0.7128769336786827, 0.5200381537675546, 0.362007817883706], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 60.0, 60, 60, 60.0, 60.0, 60.0, 60.0, 16.666666666666668, 34.521484375, 5.485026041666667], "isController": false}, {"data": ["POST /api/communication/listes-distribution/{id}/conversation", 58, 0, 0.0, 2.53448275862069, 1, 7, 2.0, 4.0, 5.049999999999997, 7.0, 0.6974171516521572, 0.3691407189408879, 0.40115107648742243], "isController": false}, {"data": ["GET /api/communication/groupes/{id}/membres", 60, 0, 0.0, 1.5333333333333332, 1, 2, 2.0, 2.0, 2.0, 2.0, 0.7125636853793808, 0.3799411838058026, 0.34166872023562106], "isController": false}, {"data": ["GET /api/communication/conversations", 66, 0, 0.0, 3.7575757575757573, 1, 8, 4.0, 6.0, 7.0, 8.0, 0.5761625826051279, 6.071173402021807, 0.270638869368229], "isController": false}, {"data": ["GET /api/communication/messages/recherche", 60, 0, 0.0, 3.8833333333333315, 1, 7, 4.0, 6.0, 6.949999999999996, 7.0, 0.7126313914127917, 0.3660587030108677, 0.3382215392838055], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 70, 0, 0.0, 1.3857142857142855, 1, 3, 1.0, 2.0, 2.0, 3.0, 0.6002606846401866, 0.37524666962509434, 0.28313077214961924], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}", 60, 0, 0.0, 1.616666666666667, 0, 7, 2.0, 2.0, 2.9499999999999957, 7.0, 0.7125552230297848, 0.41101818203410767, 0.3646278680347727], "isController": false}, {"data": ["POST /api/communication/appels/{id}/decrocher", 60, 0, 0.0, 3.016666666666666, 1, 26, 2.0, 5.899999999999999, 7.0, 26.0, 0.7128769336786827, 0.36618483116697953, 0.3680412815151011], "isController": false}, {"data": ["GET /api/communication/pieces-jointes/{id}", 60, 0, 0.0, 1.4833333333333334, 0, 4, 1.0, 2.0, 2.0, 4.0, 0.7126313914127917, 0.3716261357562801, 0.35121221568976785], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/messages", 60, 0, 0.0, 2.533333333333333, 1, 14, 2.0, 4.0, 4.0, 14.0, 0.713063320022818, 0.356531660011409, 0.4017944684112949], "isController": false}, {"data": ["POST /api/communication/appels/{id}/refuser", 60, 0, 0.0, 2.7333333333333334, 1, 19, 2.0, 4.0, 6.949999999999996, 19.0, 0.7129362278544185, 0.3662152889174063, 0.3666794401074157], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/participants", 60, 0, 0.0, 3.316666666666668, 1, 7, 3.0, 5.899999999999999, 6.0, 7.0, 0.7130802690689548, 0.3628074415868413, 0.421302307408904], "isController": false}]}, function(index, item){
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
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 2271, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
