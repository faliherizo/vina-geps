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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [1.0, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "POST /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/non-lus"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/acheminement/unites"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/acheminement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/brouillon"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/brouillons"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/acheminement/unites/{uniteId}"], "isController": false}, {"data": [1.0, 500, 1500, "PUT /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/conversations/{id}/participants/{personId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}/destinataires"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/{id}/lectures"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "DELETE /api/communication/acheminement/unites/{uniteId}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/archiver"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/raccrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/messages/{id}/lu"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/listes-distribution/{id}/conversation"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/groupes/{id}/membres"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/conversations"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/messages/recherche"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/appels/en-cours"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/listes-distribution/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/decrocher"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/communication/pieces-jointes/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/messages"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/conversations/{id}/participants"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/communication/appels/{id}/refuser"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 1221, 0, 0.0, 3.688779688779686, 1, 66, 3.0, 6.0, 7.0, 17.0, 18.099614586421584, 18.46654209438927, 9.288953639193597], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["POST /api/communication/conversations", 35, 0, 0.0, 5.457142857142856, 3, 17, 5.0, 7.799999999999997, 11.39999999999997, 17.0, 0.792016473942658, 0.6806391572944717, 0.45556416323459525], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/brouillon", 20, 0, 0.0, 3.1999999999999993, 2, 6, 3.0, 5.0, 5.949999999999999, 6.0, 2.1175224986765486, 1.058451065378507, 1.1352732927474853], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 40, 0, 0.0, 5.775, 3, 10, 6.0, 8.0, 8.0, 10.0, 0.8529512111907199, 2.1094716233793926, 0.40648456158307744], "isController": false}, {"data": ["PUT /api/communication/conversations/{id}/brouillon", 33, 0, 0.0, 4.303030303030303, 2, 24, 4.0, 6.0, 11.399999999999949, 24.0, 0.7631998889891071, 0.43390732324984393, 0.4307905623395546], "isController": false}, {"data": ["GET /api/communication/acheminement/unites", 40, 0, 0.0, 2.9000000000000017, 1, 8, 3.0, 4.899999999999999, 5.0, 8.0, 0.8522969402539845, 1.4515682263700673, 0.4045081181283559], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 40, 0, 0.0, 3.475, 2, 6, 3.0, 5.0, 5.0, 6.0, 0.8531331314251589, 0.8527165625133302, 0.4415630465384123], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 40, 0, 0.0, 2.4, 1, 4, 2.5, 4.0, 4.0, 4.0, 0.8532605217688091, 0.41413132746005676, 0.43912919430875236], "isController": false}, {"data": ["GET /api/communication/listes-distribution", 40, 0, 0.0, 2.65, 1, 4, 3.0, 3.8999999999999986, 4.0, 4.0, 0.8537522410996329, 2.4920561021941436, 0.40519881755314613], "isController": false}, {"data": ["GET /api/communication/brouillons", 40, 0, 0.0, 2.0500000000000003, 1, 4, 2.0, 3.0, 3.0, 4.0, 0.8527330092947898, 0.4122098042977744, 0.3972203568687644], "isController": false}, {"data": ["PUT /api/communication/acheminement/unites/{uniteId}", 40, 0, 0.0, 6.450000000000002, 3, 66, 5.0, 6.0, 12.699999999999974, 66.0, 0.8553588230262595, 0.5504701800530322, 0.5003514990163374], "isController": false}, {"data": ["PUT /api/communication/listes-distribution/{id}", 23, 0, 0.0, 5.391304347826087, 2, 29, 4.0, 8.0, 24.79999999999994, 29.0, 0.6023465325790908, 0.3553414879268804, 0.3635255440760528], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/participants/{personId}", 20, 0, 0.0, 3.0500000000000003, 2, 10, 3.0, 4.0, 9.699999999999996, 10.0, 2.1175224986765486, 1.0897796453149815, 1.1456127580730544], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/lu", 31, 0, 0.0, 6.161290322580647, 3, 23, 5.0, 7.800000000000001, 13.999999999999979, 23.0, 0.7340753019180677, 0.3627364284868577, 0.3885437633199148], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}/destinataires", 40, 0, 0.0, 2.4500000000000006, 1, 5, 2.5, 3.8999999999999986, 4.0, 5.0, 0.8539527337161887, 0.4745108451997182, 0.4478248222710873], "isController": false}, {"data": ["GET /api/communication/messages/{id}/lectures", 40, 0, 0.0, 2.424999999999999, 1, 6, 2.0, 3.8999999999999986, 4.0, 6.0, 0.8542080423687188, 0.4358629903688043, 0.4346117090567408], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 40, 0, 0.0, 3.9250000000000003, 2, 6, 4.0, 5.0, 6.0, 6.0, 0.8533515381661475, 1.2687979010218884, 0.43834268464393905], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 40, 0, 0.0, 2.2500000000000004, 1, 5, 2.0, 3.8999999999999986, 4.0, 5.0, 0.8530603540200469, 1.0305035721902325, 0.430695510769887], "isController": false}, {"data": ["GET /api/communication/groupes", 40, 0, 0.0, 2.1, 1, 5, 2.0, 3.8999999999999986, 4.0, 5.0, 0.8535154166222128, 0.5651205590525978, 0.3950842846473914], "isController": false}, {"data": ["DELETE /api/communication/listes-distribution/{id}", 20, 0, 0.0, 3.75, 2, 17, 3.0, 4.900000000000002, 16.39999999999999, 17.0, 2.1177467174925875, 1.1112999986764083, 1.1271210556967386], "isController": false}, {"data": ["DELETE /api/communication/acheminement/unites/{uniteId}", 20, 0, 0.0, 3.5, 2, 7, 3.0, 5.900000000000002, 6.949999999999999, 7.0, 2.1155066638459914, 1.1420430505606092, 1.066017029828644], "isController": false}, {"data": ["POST /api/communication/listes-distribution", 25, 0, 0.0, 5.28, 3, 14, 5.0, 8.400000000000002, 12.499999999999996, 14.0, 0.6377713717186663, 0.4440732305033292, 0.37992239916834614], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/archiver", 20, 0, 0.0, 3.3, 2, 7, 3.0, 5.900000000000002, 6.949999999999999, 7.0, 2.1168501270110074, 1.3930072832874683, 1.1328455757832345], "isController": false}, {"data": ["POST /api/communication/appels/{id}/raccrocher", 20, 0, 0.0, 5.699999999999999, 2, 59, 3.0, 6.700000000000006, 56.39999999999996, 59.0, 2.1168501270110074, 1.0873663738357324, 1.0929479122565622], "isController": false}, {"data": ["GET /api/communication/appels", 40, 0, 0.0, 2.5500000000000007, 1, 6, 2.0, 4.0, 4.949999999999996, 6.0, 0.8525330889405145, 0.5178472473837891, 0.3937970225281869], "isController": false}, {"data": ["POST /api/communication/messages/{id}/lu", 20, 0, 0.0, 3.5500000000000003, 2, 10, 3.0, 4.900000000000002, 9.749999999999996, 10.0, 2.114388413151496, 1.040675547098002, 1.1088150174437044], "isController": false}, {"data": ["POST /api/communication/appels", 39, 0, 0.0, 4.256410256410256, 2, 24, 4.0, 6.0, 7.0, 24.0, 0.8438635970226761, 0.6155919013436905, 0.42770039731911025], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 60.0, 60, 60, 60.0, 60.0, 60.0, 60.0, 16.666666666666668, 34.47265625, 5.485026041666667], "isController": false}, {"data": ["POST /api/communication/listes-distribution/{id}/conversation", 21, 0, 0.0, 3.952380952380953, 2, 12, 3.0, 7.400000000000002, 11.599999999999994, 12.0, 0.5632291806356444, 0.32420251106343034, 0.3234167560681239], "isController": false}, {"data": ["GET /api/communication/groupes/{id}/membres", 40, 0, 0.0, 2.125, 1, 4, 2.0, 3.0, 4.0, 4.0, 0.8536429211660762, 0.45516507319988053, 0.40848147594861073], "isController": false}, {"data": ["GET /api/communication/conversations", 40, 0, 0.0, 3.45, 2, 6, 3.0, 4.0, 4.949999999999996, 6.0, 0.8528420962858726, 5.487672434011342, 0.3997697326340028], "isController": false}, {"data": ["GET /api/communication/messages/recherche", 40, 0, 0.0, 4.05, 3, 6, 4.0, 5.0, 5.949999999999996, 6.0, 0.8540621330201772, 0.4387076972349738, 0.40451185011209567], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 40, 0, 0.0, 2.3, 1, 4, 2.0, 3.0, 3.9499999999999957, 4.0, 0.8525876033762468, 0.5178803606445562, 0.40131564924546], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}", 40, 0, 0.0, 2.475, 1, 4, 3.0, 4.0, 4.0, 4.0, 0.8538615890364172, 0.5128172629466764, 0.43610313580668575], "isController": false}, {"data": ["POST /api/communication/appels/{id}/decrocher", 37, 0, 0.0, 3.8648648648648654, 1, 25, 3.0, 7.200000000000003, 10.600000000000023, 25.0, 0.817824145705318, 0.42009326234472394, 0.4214315514345078], "isController": false}, {"data": ["GET /api/communication/pieces-jointes/{id}", 40, 0, 0.0, 3.1500000000000004, 1, 37, 2.0, 4.0, 5.949999999999996, 37.0, 0.8543904991776491, 0.4455512954695944, 0.4202491549543969], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/messages", 29, 0, 0.0, 6.89655172413793, 2, 28, 6.0, 11.0, 22.0, 28.0, 0.7034907697159353, 0.4550326880624894, 0.3957135579652136], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/participants", 27, 0, 0.0, 5.333333333333333, 2, 30, 4.0, 7.199999999999999, 21.199999999999953, 30.0, 0.6714079673745462, 0.6759976702765207, 0.39602579325608], "isController": false}, {"data": ["POST /api/communication/appels/{id}/refuser", 20, 0, 0.0, 2.45, 2, 4, 2.0, 3.0, 3.9499999999999993, 4.0, 2.1168501270110074, 1.0873663738357324, 1.0867462029000847], "isController": false}]}, function(index, item){
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
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 1221, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
