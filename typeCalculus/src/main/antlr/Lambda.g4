grammar Lambda;

expression
    : VARIABLE | function | application
    ;

function
    : '\\' VARIABLE '.' scope
    ;

application
    : '(' expression expression ')'
    ;

scope
    : expression
    ;

VARIABLE
    : [a-z] [a-z0-9’]*
    ;

WS
    : [ \n\t\r] -> skip
    ;