var $num1;
var $num2;
var $result;

$(document).ready(function() {
    $num1 = $('#number1');
    $num2 = $('#number2');
    $result = $('#result');
    
    $('#calculate').click(calculate);
    $('#reset').click(reset);
});

/**
 * Performs calculation
 */
function calculate() {
    /* Read entered numbers */
    var a = Number($num1.val());
    var b = Number($num2.val());
    
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


