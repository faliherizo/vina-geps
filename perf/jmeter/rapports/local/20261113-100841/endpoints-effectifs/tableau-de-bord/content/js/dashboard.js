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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [1.0, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "GET /api/effectifs/absences/{id}/comblement"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/remplacements"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}/postes-disponibles"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/journal"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/liaisons/organisation"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/milieux"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/moi"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/remplacements/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/integrations/connecteurs"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/moi"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/ressources/{id}/quarts"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/periodes/{periodeId}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/places/{placeId}/candidats"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/configuration/paquet"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/absences/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/remplacements/{id}/interesses"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/places/{placeId}/demandables"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/ma-ressource"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/echanges/{id}"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/integrations/messages"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/agph/campagnes/{id}/disponibilites"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/absences/{id}/impact"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/effectifs/absences"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 126, 0, 0.0, 7.484126984126985, 1, 177, 3.0, 10.599999999999994, 28.44999999999989, 154.32000000000033, 4.111332267432376, 7.011066794873887, 1.985085459017196], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["GET /api/effectifs/absences/{id}/comblement", 5, 0, 0.0, 3.8, 2, 10, 2.0, 10.0, 10.0, 10.0, 0.6400409626216078, 0.3555227534562212, 0.3047695052483359], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes", 5, 0, 0.0, 21.0, 2, 93, 3.0, 93.0, 93.0, 93.0, 0.6325110689437066, 0.8048456277672359, 0.2952541903858317], "isController": false}, {"data": ["GET /api/effectifs/agph/remplacements", 5, 0, 0.0, 3.8, 2, 7, 3.0, 7.0, 7.0, 7.0, 0.6327512022272843, 0.3058709424829157, 0.29783796823588965], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}/postes-disponibles", 5, 0, 0.0, 10.0, 2, 33, 5.0, 33.0, 33.0, 33.0, 0.6325110689437066, 0.5083560641998734, 0.32119702719797594], "isController": false}, {"data": ["GET /api/effectifs/echanges", 5, 0, 0.0, 3.2, 2, 6, 3.0, 6.0, 6.0, 6.0, 0.6358087487283826, 0.30734895568413023, 0.29306809511698884], "isController": false}, {"data": ["GET /api/effectifs/journal", 2, 0, 0.0, 10.0, 6, 14, 10.0, 14.0, 14.0, 14.0, 1.098901098901099, 59.163590315934066, 0.5054515796703296], "isController": false}, {"data": ["GET /api/effectifs/liaisons/organisation", 2, 0, 0.0, 7.5, 3, 12, 7.5, 12.0, 12.0, 12.0, 1.1037527593818985, 0.6693656870860927, 0.5227735237306843], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/periodes/{periodeId}/ressources/{ressourceId}", 5, 0, 0.0, 2.8, 2, 4, 3.0, 4.0, 4.0, 4.0, 0.6347594261774787, 0.3080814793068427, 0.3372159451567856], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}", 5, 0, 0.0, 5.6, 2, 14, 3.0, 14.0, 14.0, 14.0, 0.6305170239596469, 0.4969015999369483, 0.3084853798865069], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}/disponibilites/moi", 5, 0, 0.0, 4.8, 2, 7, 6.0, 7.0, 7.0, 7.0, 0.6317917614354309, 0.30664111858731363, 0.32083175385392976], "isController": false}, {"data": ["GET /api/effectifs/milieux", 1, 0, 0.0, 4.0, 4, 4, 4.0, 4.0, 4.0, 4.0, 250.0, 280.76171875, 114.990234375], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/moi", 5, 0, 0.0, 3.8, 2, 7, 3.0, 7.0, 7.0, 7.0, 0.6341154090044389, 0.6124415424857325, 0.2984801046290425], "isController": false}, {"data": ["GET /api/effectifs/agph/remplacements/{id}", 5, 0, 0.0, 10.8, 3, 37, 4.0, 37.0, 37.0, 37.0, 0.6328312871788382, 0.3775975746740919, 0.29849366377673714], "isController": false}, {"data": ["GET /api/effectifs/integrations/connecteurs", 3, 0, 0.0, 4.0, 2, 8, 2.0, 8.0, 8.0, 8.0, 0.7792207792207793, 1.480062905844156, 0.37134740259740256], "isController": false}, {"data": ["GET /api/effectifs/echanges/moi", 5, 0, 0.0, 2.4, 1, 3, 3.0, 3.0, 3.0, 3.0, 0.6360513929525506, 0.6633817262434805, 0.2956645146927872], "isController": false}, {"data": ["GET /api/effectifs/echanges/ressources/{id}/quarts", 4, 0, 0.0, 2.75, 2, 3, 3.0, 3.0, 3.0, 3.0, 0.6820119352088662, 0.5068467604433078, 0.34167199488491046], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/periodes/{periodeId}", 5, 0, 0.0, 2.6, 2, 4, 2.0, 4.0, 4.0, 4.0, 0.6341154090044389, 0.3721712507926443, 0.31581919784400764], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/places/{placeId}/candidats", 5, 0, 0.0, 5.2, 2, 14, 3.0, 14.0, 14.0, 14.0, 0.6347594261774787, 0.4026755109813381, 0.32109900660149804], "isController": false}, {"data": ["GET /api/effectifs/configuration/paquet", 5, 0, 0.0, 7.4, 3, 20, 4.0, 20.0, 20.0, 20.0, 0.6327512022272843, 2.7676685886484433, 0.2990738104277398], "isController": false}, {"data": ["GET /api/effectifs/absences/{id}", 5, 0, 0.0, 39.6, 3, 177, 6.0, 177.0, 177.0, 177.0, 0.6257039169065198, 0.4087850785258416, 0.2917100096984107], "isController": false}, {"data": ["POST /api/auth/login", 1, 0, 0.0, 70.0, 70, 70, 70.0, 70.0, 70.0, 70.0, 14.285714285714285, 29.589843749999996, 4.701450892857142], "isController": false}, {"data": ["GET /api/effectifs/agph/remplacements/{id}/interesses", 5, 0, 0.0, 7.2, 2, 19, 5.0, 19.0, 19.0, 19.0, 0.6327512022272843, 0.33985660275879526, 0.30463510029106555], "isController": false}, {"data": ["GET /api/effectifs/echanges/places/{placeId}/demandables", 5, 0, 0.0, 3.0, 2, 5, 3.0, 5.0, 5.0, 5.0, 0.6362942224484602, 0.3075836329218631, 0.31938987337744973], "isController": false}, {"data": ["GET /api/effectifs/ma-ressource", 1, 0, 0.0, 2.0, 2, 2, 2.0, 2.0, 2.0, 2.0, 500.0, 386.71875, 232.421875], "isController": false}, {"data": ["GET /api/effectifs/echanges/{id}", 4, 0, 0.0, 3.5, 3, 4, 3.5, 4.0, 4.0, 4.0, 0.6821282401091405, 0.4003506565484311, 0.3150846265347885], "isController": false}, {"data": ["GET /api/effectifs/disponibilites/periodes/{periodeId}/attribution-automatique", 5, 0, 0.0, 4.0, 2, 10, 2.0, 10.0, 10.0, 10.0, 0.6341958396752917, 0.37097979293505834, 0.3307232210806697], "isController": false}, {"data": ["GET /api/effectifs/integrations/messages", 3, 0, 0.0, 3.0, 2, 4, 3.0, 4.0, 4.0, 4.0, 0.7800312012480499, 0.3770658638845554, 0.3694483716848674], "isController": false}, {"data": ["GET /api/effectifs/agph/campagnes/{id}/disponibilites", 5, 0, 0.0, 10.6, 2, 35, 4.0, 35.0, 35.0, 35.0, 0.6316321374431532, 0.397237398938858, 0.3182833817584639], "isController": false}, {"data": ["GET /api/effectifs/absences/{id}/impact", 5, 0, 0.0, 2.6, 2, 4, 2.0, 4.0, 4.0, 4.0, 0.6402048655569782, 0.34673595550576186, 0.30234675096030733], "isController": false}, {"data": ["GET /api/effectifs/absences", 5, 0, 0.0, 3.0, 2, 4, 3.0, 4.0, 4.0, 4.0, 0.6255473539346929, 0.5760655808207181, 0.28833823345427245], "isController": false}]}, function(index, item){
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
