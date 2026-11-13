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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 1230, 0, 0.0, 3.7219512195121953, 0, 107, 2.0, 5.0, 7.0, 50.070000000000164, 18.183968540256053, 22.741005149352475, 9.356791776928537], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["POST /api/communication/conversations", 36, 0, 0.0, 4.083333333333332, 2, 15, 3.5, 6.300000000000004, 9.899999999999991, 15.0, 0.8032307726633794, 0.6902764452575916, 0.46279898034315803], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/brouillon", 20, 0, 0.0, 2.75, 1, 4, 3.0, 4.0, 4.0, 4.0, 2.1193175797393238, 1.0593483429585675, 1.138305340680301], "isController": false}, {"data": ["GET /api/communication/conversations/non-lus", 40, 0, 0.0, 4.975, 2, 17, 5.0, 8.0, 11.849999999999987, 17.0, 0.8549748851127498, 2.1144764614726945, 0.4082839050977878], "isController": false}, {"data": ["PUT /api/communication/conversations/{id}/brouillon", 34, 0, 0.0, 2.764705882352941, 2, 6, 2.5, 4.0, 5.25, 6.0, 0.7760076687816679, 0.4398922148171817, 0.438777773656822], "isController": false}, {"data": ["GET /api/communication/acheminement/unites", 40, 0, 0.0, 2.8500000000000005, 2, 8, 3.0, 4.0, 4.0, 8.0, 0.8550662676357417, 5.623897766139375, 0.4066574925181701], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/acheminement", 40, 0, 0.0, 2.8249999999999997, 1, 7, 3.0, 4.0, 5.949999999999996, 7.0, 0.8549383375724026, 0.8545208872122598, 0.44333228247162676], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/brouillon", 40, 0, 0.0, 1.6750000000000003, 1, 6, 1.0, 2.0, 4.949999999999996, 6.0, 0.8547191179298703, 0.41483925938588434, 0.4407145451825894], "isController": false}, {"data": ["GET /api/communication/listes-distribution", 40, 0, 0.0, 3.4749999999999996, 1, 36, 2.0, 5.0, 19.29999999999994, 36.0, 0.8541898009737763, 2.493333315537712, 0.40624065729905184], "isController": false}, {"data": ["GET /api/communication/brouillons", 40, 0, 0.0, 1.4000000000000001, 0, 8, 1.0, 2.0, 2.0, 8.0, 0.8551576696953501, 0.41338188134687337, 0.3991849278460716], "isController": false}, {"data": ["PUT /api/communication/acheminement/unites/{uniteId}", 40, 0, 0.0, 5.000000000000001, 2, 32, 3.5, 9.699999999999996, 20.499999999999957, 32.0, 0.8541533205210335, 0.549694373265001, 0.5004804612427931], "isController": false}, {"data": ["PUT /api/communication/listes-distribution/{id}", 24, 0, 0.0, 3.4166666666666665, 2, 12, 3.0, 5.5, 10.5, 12.0, 0.6191311526158292, 0.36367909013517696, 0.3742599447941389], "isController": false}, {"data": ["DELETE /api/communication/conversations/{id}/participants/{personId}", 20, 0, 0.0, 1.8999999999999997, 1, 3, 2.0, 2.0, 2.9499999999999993, 3.0, 2.11954217888936, 1.0908190705807546, 1.1487753020347604], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/lu", 32, 0, 0.0, 5.09375, 2, 14, 5.0, 9.399999999999999, 11.399999999999991, 14.0, 0.7475238273219958, 0.36938189123528314, 0.39639202952719116], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}/destinataires", 40, 0, 0.0, 4.9, 1, 53, 2.0, 6.699999999999996, 43.19999999999993, 53.0, 0.8533515381661475, 0.4741767824380253, 0.4483428979818236], "isController": false}, {"data": ["GET /api/communication/messages/{id}/lectures", 40, 0, 0.0, 5.824999999999999, 1, 78, 2.0, 6.899999999999999, 46.69999999999989, 78.0, 0.8533333333333333, 0.4354166666666667, 0.435], "isController": false}, {"data": ["GET /api/communication/conversations/{id}/messages", 40, 0, 0.0, 3.3000000000000007, 1, 9, 3.0, 5.899999999999999, 7.949999999999996, 9.0, 0.8546825922523023, 1.270776966571227, 0.439861060661097], "isController": false}, {"data": ["GET /api/communication/conversations/{id}", 40, 0, 0.0, 1.6999999999999997, 1, 7, 1.5, 2.0, 4.849999999999987, 7.0, 0.8549931600547196, 1.032838416980164, 0.43250630557455544], "isController": false}, {"data": ["GET /api/communication/groupes", 40, 0, 0.0, 2.5, 1, 28, 1.5, 4.799999999999997, 7.0, 28.0, 0.8547008547008547, 2.578291933760684, 0.39646768162393164], "isController": false}, {"data": ["DELETE /api/communication/listes-distribution/{id}", 20, 0, 0.0, 2.2499999999999996, 1, 3, 2.0, 3.0, 3.0, 3.0, 2.12021626205873, 1.112595906657479, 1.130505936605534], "isController": false}, {"data": ["DELETE /api/communication/acheminement/unites/{uniteId}", 20, 0, 0.0, 6.6499999999999995, 1, 70, 2.0, 11.400000000000013, 67.09999999999997, 70.0, 2.1072595090085344, 1.137590875566326, 1.063919107575598], "isController": false}, {"data": ["POST /api/communication/listes-distribution", 26, 0, 0.0, 4.115384615384615, 2, 18, 3.0, 6.300000000000001, 14.149999999999984, 18.0, 0.6535783414192705, 0.4550794506171288, 0.38997692051481864], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/archiver", 20, 0, 0.0, 2.8499999999999996, 1, 6, 2.0, 4.900000000000002, 5.949999999999999, 6.0, 2.118868524208073, 1.394335502966416, 1.1359949412013985], "isController": false}, {"data": ["POST /api/communication/appels/{id}/raccrocher", 20, 0, 0.0, 4.55, 1, 51, 2.0, 4.900000000000002, 48.69999999999997, 51.0, 2.108370229812355, 1.0830104891418932, 1.0912463103520977], "isController": false}, {"data": ["GET /api/communication/appels", 40, 0, 0.0, 1.5499999999999998, 1, 3, 2.0, 2.0, 2.0, 3.0, 0.8551393877201984, 0.5194303702753549, 0.3958360056439199], "isController": false}, {"data": ["POST /api/communication/messages/{id}/lu", 20, 0, 0.0, 6.35, 1, 69, 2.0, 11.400000000000013, 66.14999999999996, 69.0, 2.1079258010118043, 1.0374947301854975, 1.1074844540472175], "isController": false}, {"data": ["POST /api/communication/appels", 40, 0, 0.0, 6.0, 1, 85, 2.0, 5.0, 50.99999999999983, 85.0, 0.8542080423687188, 0.6231380934076495, 0.4337775215153651], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 53.0, 53, 53, 53.0, 53.0, 53.0, 53.0, 18.867924528301884, 39.08092570754717, 6.209463443396227], "isController": false}, {"data": ["POST /api/communication/listes-distribution/{id}/conversation", 21, 0, 0.0, 2.095238095238096, 1, 4, 2.0, 3.8000000000000007, 4.0, 4.0, 0.5625803686240892, 0.32382904589048433, 0.3235935909370981], "isController": false}, {"data": ["GET /api/communication/groupes/{id}/membres", 40, 0, 0.0, 3.225, 1, 28, 2.0, 4.899999999999999, 26.249999999999936, 28.0, 0.8542445274959958, 0.4129404698344901, 0.40960357714895884], "isController": false}, {"data": ["GET /api/communication/conversations", 40, 0, 0.0, 2.325, 1, 5, 2.5, 4.0, 4.0, 5.0, 0.8551576696953501, 5.502572153928381, 0.40169027258150725], "isController": false}, {"data": ["GET /api/communication/messages/recherche", 40, 0, 0.0, 9.15, 2, 107, 3.0, 16.9, 75.89999999999982, 107.0, 0.8533151292772421, 0.43832398242170834, 0.4049913601843161], "isController": false}, {"data": ["GET /api/communication/appels/en-cours", 40, 0, 0.0, 1.325, 1, 2, 1.0, 2.0, 2.0, 2.0, 0.8551211065267118, 0.51941926587853, 0.4033432563011736], "isController": false}, {"data": ["GET /api/communication/listes-distribution/{id}", 40, 0, 0.0, 3.8, 1, 45, 2.0, 6.799999999999997, 21.699999999999974, 45.0, 0.8533515381661475, 0.5125109335665827, 0.4366759824209583], "isController": false}, {"data": ["POST /api/communication/appels/{id}/decrocher", 38, 0, 0.0, 6.763157894736843, 1, 98, 2.0, 5.300000000000004, 66.6499999999999, 98.0, 0.8292959713674654, 0.42598601654227225, 0.42841559458338785], "isController": false}, {"data": ["GET /api/communication/pieces-jointes/{id}", 40, 0, 0.0, 2.4, 1, 17, 2.0, 3.8999999999999986, 11.749999999999979, 17.0, 0.853406157325425, 0.44503797657400096, 0.42086924750912075], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/messages", 30, 0, 0.0, 4.266666666666665, 1, 8, 4.0, 7.0, 8.0, 8.0, 0.7177376907986027, 0.46073526245274893, 0.4044283667878846], "isController": false}, {"data": ["POST /api/communication/conversations/{id}/participants", 28, 0, 0.0, 3.928571428571429, 1, 17, 3.0, 6.300000000000004, 13.399999999999977, 17.0, 0.6873695838959126, 0.6798418896526329, 0.4061119123603781], "isController": false}, {"data": ["POST /api/communication/appels/{id}/refuser", 20, 0, 0.0, 1.95, 1, 4, 2.0, 3.0, 3.9499999999999993, 4.0, 2.1186440677966103, 1.088287870762712, 1.0903568591101696], "isController": false}]}, function(index, item){
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
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 1230, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
