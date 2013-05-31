var $num1;
var $num2;
var $result;
var db;

document.addEventListener("deviceready", deviceready, true);

function deviceready() {
    $num1 = $('#number1');
    $num2 = $('#number2');
    $result = $('#result');
    
    $('#calculate').click(calculate);
    $('#reset').click(reset);
    
    $('#addKey').click(addKey);
    $('#getKey').click(getKey);
    $('#removeKey').click(removeKey);
    $('#clear').click(clear);
    
    db = JsHybugger.openDatabase('mydb', '1.0', 'Test DB', 50 * 1024 * 1024);
    db.transaction(function(tx) {
        tx.executeSql("CREATE TABLE IF NOT EXISTS " +
                      "todo(ID INTEGER PRIMARY KEY ASC, todo TEXT, added_on DATETIME)", []);

        tx.executeSql("CREATE TABLE IF NOT EXISTS " +
                "empty(ID INTEGER PRIMARY KEY ASC, todo TEXT, added_on DATETIME)", []);
    });
    addTodo("task one");
    addTodo("task two");
}

function addTodo(todoText) {
  db.transaction(function(tx){
    var addedOn = new Date();
    tx.executeSql("INSERT INTO todo(todo, added_on) VALUES (?,?)",
        [todoText, addedOn]);
   });
}

function getStorage() {
	return $('#storageType')[0].checked ? sessionStorage : localStorage;
}
function clear() {
	getStorage().clear();
}
function getKey() {
	var x=1;
	$('#value').val(getStorage().getItem($('#key').val()));
}

function addKey() {
	getStorage().setItem($('#key').val(), $('#value').val());
}

function removeKey() {
	getStorage().removeItem($('#key').val());
}

/**
 * Performs calculation
 */
function calculate() {
    /* Read entered numbers */
    var a = Number($num1.val());
    var b = Number($num2.val());
    var c = {
    		a : 1,
    		b : 1.1,
    		c : 'hi',
    		d : {
        		a : 2,
        		b : 3.3,
        		c : 'ho',
    		}
    };
    
    if (isNaN(a) || isNaN(b)) {
    	$result.text('Error');
    	throw new Error("a or b is null");
    }

    function addNumbers(n1, n2) {
        console.log('Performing addition of '+n1+' and '+n2+'.');
        return n1 + n2;
    };
    
    var sum = addNumbers(a, b);
    if (sum == 10) {
    	debugger;
    }
    
    /* Update result field */
    $result.text(sum);
    console.log('calculated sum: ' + sum);
}

/**
 * Clears the result and input fields
 */
function reset() {
    $num1.val('');
    $num2.val('');
    $result.text('');
}


