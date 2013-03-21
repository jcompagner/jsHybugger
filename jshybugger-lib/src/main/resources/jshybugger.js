/*
 * Copyright 2013 Wolfgang Flohr-Hochbichler (developer@jshybugger.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* 
 * JsHybugger runtime library.
 */
window.JsHybugger = (function() {
    var breakpoints = {};
    var breakpointsById = {};
    var shouldBreak = function() { return false; };
    var lastFile = '';
    var lastLine = '';
    var callStack = [];
    var callStackDepth = 0;
	var NOT_INITIALIZED = window['JsHybuggerNI'] === undefined;
	
	if (NOT_INITIALIZED) {
		console.info("JsHybugger core not loaded, propably you use it outside a native app.")
	}
    
	/**
	 * Processes pending debugger queue messages.
	 * @param {boolean} block call will block until break-able message is received
	 */
    function processMessages(block) {
		if (NOT_INITIALIZED) return;
		
    	var msg = null;
    	if (block) {
	       	while ((msg = JsHybuggerNI.getQueuedMessage(true)) != null) {
	       		if (!processCommand(parseSafe(msg), callStack[callStack.length-1])) {
	       			break;
	       		}
	       	}
    	}
    	
    	// before returning process all pending queue messages
    	while ((msg = JsHybuggerNI.getQueuedMessage(false)) != null) {
    		processCommand(parseSafe(msg), block ? callStack[callStack.length-1] : null);
    	}
    }
    
    /**
     * Send message to debugging server for processing by message handlers.
	 * @param {String} path message handler name
	 * @param {object} JSON payload
     * 
     */
    function sendToDebugService(path, payload) {
		if (NOT_INITIALIZED) return;
		
        try {
       		return parseSafe(JsHybuggerNI.sendToDebugService(path, JSON.stringify(payload)));
        } catch (ex) {
            console.error('JsHybugger sendToDebugService failed: ' + ex.toString());
        }
    }
    
    /**
     * Wrap browser console interface and dispatch messages to the debug server.
     */
    function replaceConsole() {
		if (NOT_INITIALIZED) return;

        if (!window.console) {
            window.console = {};
        }
        
        ['info', 'log', 'warn', 'error'].forEach(function(f) {
            var oldFunc = window.console[f];
            
            window.console[f] = function() {
			
                var args = Array.prototype.slice.call(arguments);
                /* Write to local console first */
                oldFunc && oldFunc.apply(window.console, args);
                
                sendToDebugService('Console.messageAdded', { 
                    type: f.toUpperCase(),
                    message: args.toString()
                });
            };
        });
    }
    
    /**
	 * @return {object} stack object created by pushStack method. 
     * @param {string} objectId object identifier i.e. stack:0:varname 
     */
    function getStackForObjectId(objectId) {
    	if (objectId) {
	    	var objectParams = objectId.split(":");
	    	return objectParams.length > 1 ? callStack[objectParams[1]] : null;
    	} 
    	return null; 
	}

    /**
     * Debugger message processing.
	 * @param {object} cmd message from debug server
	 * @param {object} stack scope for resolving variables on call stack
	 * @return {boolean} true for non break-able messages 
     */
    function processCommand(cmd,stack) {
		if (NOT_INITIALIZED) return;

		if (cmd) {
	        switch (cmd.command) {
	        	case 'callFunctionOn':
	        		return runSafe(function() {
	        			var objectParams = cmd.data.params.objectId.split(":");
	        			stack = callStack[objectParams[1]] || stack;

	        			var response = {
	        					result : stack.evalScope(cmd.data.expression)
	        			};
	        			
	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify(response));
	        			
	        		}, true);
	        		
	        	case 'eval':
	        		return runSafe(function() {
	        			doEval(getStackForObjectId(cmd.data.params.callFrameId) || stack, cmd);
	        		}, true);
	        		
	        	case 'getProperties':
	        		return runSafe(function() {
	        			getProperties(stack.evalScope, cmd);
	        		}, true);
	        		        		
	            case 'breakpoint-set':
	        		return runSafe(function() {
		                var file = cmd.data.url;
		                var line = cmd.data.lineNumber;
		                if (!breakpoints[file]) {
		                    breakpoints[file] = {};
		                }
		                var breakpointId= file + ":" + line;
		                breakpoints[file][line] = breakpointId;
		                
		                //console.log("set-breakpoint: " + ((breakpoints[file] && breakpoints[file][line]) || false ) + ", file: " + file + ", line: "+ line);
		                breakpointsById[breakpointId] = cmd.data;
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify({ breakpointId : breakpointId, lineNumber : line }));
	        		}, true);
	                
	
	            case 'breakpoint-remove':
	        		return runSafe(function() {
		                var data = breakpointsById[cmd.data.breakpointId];
		                if (data) {
		                	//console.log("remove-breakpoint: " + cmd.data.breakpointId);
		                	
			                delete breakpointsById[cmd.data.breakpointId];
			                delete breakpoints[data.url][data.lineNumber]; 
		                	                
			                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify({ breakpointId : cmd.data.breakpointId}));
		                }
	        		}, true);
	            
	            case 'breakpoint-resume':
	        		return runSafe(function() {
	        			shouldBreak = function() { return false; };
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify({ }));
	        		}, false);
	            
	            case 'breakpoint-step-over':
	        		return runSafe(function() {
		                shouldBreak = (function(oldDepth) {
		                    return function(depth) {
		                        return depth <= oldDepth;
		                    };
		                })(callStackDepth);
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify({ }));
	        		}, false);
	
	            case 'breakpoint-step-into':
	        		return runSafe(function() {
	        			shouldBreak = function() { return true; };
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify({ }));
	        		}, false);
	                
	            case 'breakpoint-step-out':
	        		return runSafe(function() {
		                shouldBreak = (function(oldDepth) {
		                    return function(depth) {
		                        return depth < oldDepth;
		                    };
		                })(callStackDepth);
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify({ }));
	        		}, false);
	                
	            case 'page-reload':
	            	return runSafe(function() {
	        			shouldBreak = function() { return false; };
	        			breakpoints = {};
	        			setTimeout(function() {
	        				location.reload();
	        			}, 500);
	        		}, false);
	            	
	        	default:
	        		console.warn('JsHybugger unknown command received:' + cmd.command);
	        }
		}
    }
    
    /**
     * Handles "getProperties" messages and send back result to debugger client.
	 * @param {object} evalScopeFunc scope function for resolving variables on call stack
	 * @param {object} cmd message from debug server
     */
    function getProperties(evalScopeFunc, cmd) {
		if (NOT_INITIALIZED) return;
		
		var objectParams = cmd.data.objectId.split(":");
		var results = [];
		
		var stack = callStack[objectParams[1]];
		
		if (objectParams.length == 2) {
			var varnames = stack.varnames ? stack.varnames.split(",") : [];
			for (var i=0; i < varnames.length; i++) {
				try {
					var expr = stack.evalScope(varnames[i]);
					var result = {};
					var oType = typeof(expr);
					result.value = {
				        type:oType,
				        description: expr + ""
				    };
					if (expr && (oType == 'object')) {
						result.value.objectId='stack:' + objectParams[1] + ":"+ varnames[i];
					} else {
						result.value.value = expr;
					}
					
			        result.writable = false;
			        result.enumerable = false;
			        result.configurable = false;
			        result.name = varnames[i];
			        results.push(result);
				} catch (e) {
					console.error("getProperties() failed for variable: " + varnames[i] + ", " + e);
				}
			}
		} else {
			var objName = objectParams[2];
			var obj = null;
			var props = objName.split('.');

			if (objName.indexOf('this') == 0) {
				obj = stack.that;
			} else if (objName.indexOf('expr') == 0) {
				obj = stack.expr;
			} else {
				obj = stack.evalScope(props[0]);
			}

			for (var i=1; obj && i < props.length; i++) {
				obj = obj[props[i]];
			}
			
			for (expr in obj) {
				var oVal = obj[expr];
				var oType = typeof(oVal);
				
				if (oType == 'function') {
					continue;
				}
				
				var result = {};
				
				result.value = {
			        type:oType,
			        description: ""
			    };
				if (oVal && oType == 'object') {
					result.value.objectId=cmd.data.objectId + "." + expr;
					result.value.description = oVal && oVal.constructor ? oVal.constructor.name : oVal;
				} else {
					result.value.value = oVal;
				}
				
		        result.writable = false;
		        result.enumerable = false;
		        result.configurable = false;
		        result.name = expr;
		        results.push(result);
			}
		}		
        
        JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify({ result : results }));
    }
    
    /**
     * Evaluates an expression in the given scope.
	 * @param {object} evalScopeFunc scope function for resolving variables on call stack
	 * @param {object} cmd message from debug server
     */
	function doEval(stack, cmd) {
		if (NOT_INITIALIZED) return;

        var response = {};
        var params = cmd.data.params;
        
        try {
            var evalResult = stack && stack.evalScope ? stack.evalScope(params.expression) : eval(params.expression);
            if (stack) {

            	response.type = typeof(evalResult);

            	if (params.returnByValue) {
            		response.description = response.value = evalResult;
            	} else {
                	var exprID = "ID" + new Date().getTime();
                	stack.expr = stack.expr || {};
                	stack.expr[exprID] = evalResult ;
            		
            		response.objectId = "stack:" + stack.depth + ":expr." + exprID;
            		if (response.type == 'object') {
            			response.description = 'object';
            		} else {
            			response.description = "" + evalResult;
            		}
            	}
            } else {
            	response = { 
            		type : typeof(evalResult), 
        			value : evalResult
        		};
            }
        } catch (ex) {
            evalResult = ex.toString();
        }  
        JsHybuggerNI.sendReplyToDebugService(cmd.replyId, JSON.stringify(response));
    }
    
	/**
	 * JSON parsing with exception handling.
	 * @param {string} str JSON string data
	 * @return {object} JSON object or null on parsing failure 
	 * 
	 */
    function parseSafe(str) {
        try {
            return JSON.parse(str);
        } catch (ex) {
            return null;
        }
    }

	/**
	 * Runs a function in an exception handled scope.
	 * @param {function} fctn function to execute
	 * @return {object} retVal value to return after function execution 
	 * 
	 */
    function runSafe(fctn, retVal) {
        try {
        	fctn();
        } catch (ex) {
            return console.log("runSafe failed: " + ex);
        } finally {
        	return retVal;
        }
    }

    /**
     * Sends "Debugger.paused" message to debug server.
     */
    function sendDebuggerPaused(reason, auxData) {
    	var callFrames = prepareStackInfo();
        sendToDebugService('Debugger.paused', {
            reason: reason,
            auxData: auxData,
            url: lastFile,
            lineNumber: lastLine,
            callFrames : callFrames
        });
    }
    
    /**
     * Prepares stack info after break has occurred.
     */
    function prepareStackInfo() {
    	var stackInfo = [];
    	frameInfo = [];
    	
    	for (var i=callStackDepth-1; i >= 0; i--) {
    		
    		var frameInfo = {};
    		frameInfo.callFrameId = "stack:"+i;
    		frameInfo.functionName = callStack[i].name;
    		frameInfo.location = {
    			scriptId : i == callStackDepth-1 ? lastFile : callStack[i+1].lastFile,
    			lineNumber : i == callStackDepth-1 ? lastLine : callStack[i+1].lastLine,
    			columnNumber : 0
    		};
    		
    		// add frame 'scopeChain[]' info
    		frameInfo.scopeChain = [];
    		var scope = { object : {}, type : 'local'};
    		
    		scope.object = {
    			type : 'object',
    			objectId : 'stack:' + i,
    			className : 'Object',
    			description : 'Object'
    		};
    		frameInfo.scopeChain.push(scope);
    		
    		// add frame 'this' info
    		frameInfo['this'] = {
        			type : typeof(callStack[i].that),
        			objectId : 'stack:' + i + ':this',
        			className : callStack[i].that.constructor.name,
        			description : callStack[i].that.constructor.name
    		};
    		
    		stackInfo.push(frameInfo);
    	}
    	return stackInfo;
    }

    /**
     * Sends 'GlobalInitHybugger' to debug server. 
     * This function is called after this script has been parsed.
     */
	function initHybugger() {
		replaceConsole();
		sendToDebugService('GlobalInitHybugger', { 
        });
	};
	
    /*
     * ---- begin public API functions
     */ 
    
    /**
     * Send 'GlobalPageLoaded' message to debug server message handlers.
     */
    function pageLoaded() {
		if (NOT_INITIALIZED) return;
        
		var cmd = sendToDebugService('GlobalPageLoaded', {        });
       	// before returning - process all pending queue messages 
       	while ((msg = JsHybuggerNI.getQueuedMessage(false)) != null) {
       		processCommand(parseSafe(msg),null);
   		}
    };
    
    /**
     * Used by the instrumented code to keep track of the actual processed statement.
	 * @param {string} file actual processed file
	 * @param {number} line actual processed line number within file
	 * @param {boolen} isDebuggerStatement true signals a debugger literal
     */
    function track(file, line, isDebuggerStatement) {
        lastFile = file;
        lastLine = line;
        
        var isBreakpoint = (breakpoints[file] && breakpoints[file][line]) || /* breakpoint set? */
                           isDebuggerStatement ||                            /* break on debugger; keyword? */
                           shouldBreak(callStackDepth);                      /* break on next (in|over|out) */

        if (!isBreakpoint) {
            return;
        }

    	sendDebuggerPaused('other', { });
    	processMessages(true);
    };
    
    /**
     * Used by the instrumented code to report thrown exceptions in the code.
     */
    function reportException(e) {
    	console.error('Exception at line: ' + lastLine + ", file: " + lastFile + ", reason: " + e.toString());
    	sendDebuggerPaused('exception', { description : e.toString() });
    }
    
    
    /**
     * Used by the instrumented code to track function entries.
     */
    function pushStack(that, evalScopeFunc, functionName, vars, file, line) {
        callStack.push({depth : callStackDepth, that : that, evalScope: evalScopeFunc, name : functionName, file : file, line : line, lastFile : lastFile, lastLine : lastLine, varnames : vars });
        ++callStackDepth;
    };
    
    /**
     * Used by the instrumented code to track function exists.
     */
    function popStack() {
        var f = callStack.pop();
        --callStackDepth;
    };
    
    /**
     * Used by the instrumented code to track javascript file loads.
     */
    function loadFile(filename, numLines) {
    	sendToDebugService('Debugger.scriptParsed', { 
            url: filename,
            numLines: numLines
        });
    };
    
    // register on load event handler
    window.addEventListener("load", pageLoaded, false);
    
    // now send the GlobalInitHybugger message
	initHybugger();

	// Return the public API for JsHybugger
    return {
    	loadFile : loadFile,
    	pushStack : pushStack,
    	popStack : popStack,
    	reportException : reportException,
    	track : track,
    	processMessages : processMessages
    }

})();
