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
if (window['JsHybugger'] === undefined) {
window.JsHybugger = (function() {
    var breakpoints = {};
    var breakpointsById = {};
    var shouldBreak = function() { return false; };
    var lastFile = '';
    var lastLine = '';
    var callStack = [];
    var callStackDepth = 0;
    var blockModeActive = false;
	var databases = [];
	var globalWatches = {};
	
	if (window['JsHybuggerNI'] === undefined) {
		console.info("JsHybugger loaded outside a native app.")
		window['JsHybuggerNI'] = {
			sendToDebugService : function(method, data) {
				
				sendXmlData('sendToDebugService', { arg0: method, arg1: data});
			},
			
			sendReplyToDebugService : function(id, data) {

				return sendXmlData('sendReplyToDebugService', { arg0: id, arg1: data});
			},
			
			getQueuedMessage : function(flag) {
				var data = sendXmlData('getQueuedMessage', { arg0: flag});
				
				return data;
			}
		};
		
		openPushChannel();
	}

    function openPushChannel() {
    	
		var pushChannel = new XMLHttpRequest();
		pushChannel.onreadystatechange = function() {
		   if(pushChannel.readyState == 3) {
			  if (pushChannel.responseText) {
				  eval(pushChannel.responseText);
			  }
		   } else if (pushChannel.readyState == 4) {
	    	  setTimeout(openPushChannel, 0);
		   }
		}
		pushChannel.timeout = 30000;
		pushChannel.open('GET', 'http://localhost:8888/jshybugger/pushChannel', true);
		pushChannel.send();
	}
    
    function sendXmlData(cmd, data) {
    	var response;
    	var xmlObj = new XMLHttpRequest();
    	xmlObj.onreadystatechange = function() {
     	   if(xmlObj.readyState == 4) {
		      response = xmlObj.status == '200' && xmlObj.responseText && xmlObj.responseText.length > 0 ? xmlObj.responseText : null;
		   }
		}
		xmlObj.open ('POST', 'http://localhost:8888/jshybugger/' + cmd, false);
		xmlObj.send (stringifySafe(data));
		
		return response;
    }
    
	/**
	 * Processes pending debugger queue messages.
	 * @param {boolean} block call will block until break-able message is received
	 */
    function processMessages(block) {
		if (!block && blockModeActive) return;
		
    	var msg = null;
    	if (block) {
    		try {
	    		blockModeActive = true;
		       	while ((msg = JsHybuggerNI.getQueuedMessage(true)) != null) {
		       		if (!processCommand(parseSafe(msg), callStack[callStack.length-1])) {
		       			break;
		       		}
		       	}
    		} finally {
    			blockModeActive = false;
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
		
        try {
       		return parseSafe(JsHybuggerNI.sendToDebugService(path, stringifySafe(payload)));
        } catch (ex) {
           // console.error('JsHybugger sendToDebugService failed: ' + ex.toString());
        }
    }
    
    /**
     * Wrap browser console interface and dispatch messages to the debug server.
     */
    function replaceConsole() {

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
	    	return objectParams.length > 1 && callStackDepth >= objectParams[1] ? callStack[objectParams[1]] : null;
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

		if (cmd) {
	        switch (cmd.command) {
	        	case 'callFunctionOn':
	        		return runSafe('callFunctionOn', function() {
	        			var objectParams = cmd.data.params.objectId.split(":");
	        			stack = callStack[objectParams[1]];

	        			var response = {
	        					result : stack ? stack.evalScope(cmd.data.expression) : eval(cmd.data.expression)
	        			};
	        			
	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe(response));
	        			
	        		}, true);
	        		
	        	case 'evaluateOnCallFrame':
	        		return runSafe('evaluateOnCallFrame', function() {
	        			doEval(getStackForObjectId(cmd.data.params.callFrameId) || stack, cmd);
	        		}, true);

	        	case 'evaluate':
	        		return runSafe('evaluate', function() {
	        			doEval(null, cmd);
	        		}, true);
	        		
	        	case 'releaseObjectGroup':
	        		return runSafe('releaseObjectGroup', function() {
	        			for (p in globalWatches) {
	        				delete globalWatches[p];
	        			}
	        		}, true);
	        	
	        	case 'getResourceContent':
	        		return runSafe('getResourceContent', function() {
	        			
	        			// determine if request resource is an image, then use content encoding, in all other cases don't encode
	        	    	var imgs = document.getElementsByTagName("img"); 
	        	    	for (var i = 0; i < imgs.length; i++) {
	        	    		var src = imgs[i].src;
	        	    		if (src && src.indexOf(cmd.data.params.url) >= 0) {
	        	    			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({base64Encoded : true}));
	        	    			return;
	        	    		}
	        	    	}
    	    			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({base64Encoded : false}));
	        	    	
	        		}, true);
	        		
	        	case 'getResourceTree':
	        		return runSafe('getResourceTree', function() {
	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe(getResourceTree(cmd)));
	        		}, true);
	        		
	        	case 'getDOMStorageItems':
	        		return runSafe('getDOMStorageItems', function() {
	        			var response = { entries : getDOMStorageItems(cmd.data.params.storageId.isLocalStorage ? localStorage : sessionStorage) };

	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe(response));
	        		}, true);

	        	case 'setDOMStorageItem':
	        		return runSafe('setDOMStorageItem', function() {
	        			if (cmd.data.params.storageId.isLocalStorage) {
	        				localStorage.setItem(cmd.data.params.key, cmd.data.params.value);
	        			} else {
	        				sessionStorage.setItem(cmd.data.params.key, cmd.data.params.value);
	        			} 

	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({}));
	        		}, true);

	        	case 'removeDOMStorageItem':
	        		return runSafe('removeDOMStorageItem', function() {
	        			if (cmd.data.params.storageId.isLocalStorage) {
	        				localStorage.removeItem(cmd.data.params.key);
	        			} else {
	        				sessionStorage.removeItem(cmd.data.params.key);
	        			} 

	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({}));
	        		}, true);

	        	case 'getProperties':
	        		return runSafe('getProperties', function() {
	        			getProperties(cmd);
	        		}, true);
	        		        		
	            case 'breakpoint-set':
	        		return runSafe('breakpoint-set',function() {
		                var file = cmd.data.url;
		                var line = cmd.data.lineNumber;
		                if (!breakpoints[file]) {
		                    breakpoints[file] = {};
		                }
		                var breakpointId= file + ":" + line;
		                breakpoints[file][line] = breakpointId;
		                
		                //console.log("set-breakpoint: " + ((breakpoints[file] && breakpoints[file][line]) || false ) + ", file: " + file + ", line: "+ line);
		                breakpointsById[breakpointId] = cmd.data;
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ breakpointId : breakpointId, lineNumber : line }));
	        		}, true);
	                
	
	            case 'breakpoint-remove':
	        		return runSafe('breakpoint-remove', function() {
		                var data = breakpointsById[cmd.data.breakpointId];
		                if (data) {
		                	//console.log("remove-breakpoint: " + cmd.data.breakpointId);
		                	
			                delete breakpointsById[cmd.data.breakpointId];
			                delete breakpoints[data.url][data.lineNumber]; 
		                	                
			                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ breakpointId : cmd.data.breakpointId}));
		                }
	        		}, true);
	            
	            case 'breakpoint-resume':
	        		return runSafe('breakpoint-resume', function() {
	        			shouldBreak = function() { return false; };
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ }));
	        		}, false);
	            
	            case 'breakpoint-step-over':
	        		return runSafe('breakpoint-step-over', function() {
		                shouldBreak = (function(oldDepth) {
		                    return function(depth) {
		                        return depth <= oldDepth;
		                    };
		                })(callStackDepth);
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ }));
	        		}, false);
	
	            case 'breakpoint-step-into':
	        		return runSafe('breakpoint-step-into', function() {
	        			shouldBreak = function() { return true; };
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ }));
	        		}, false);
	                
	            case 'breakpoint-step-out':
	        		return runSafe('breakpoint-step-out', function() {
		                shouldBreak = (function(oldDepth) {
		                    return function(depth) {
		                        return depth < oldDepth;
		                    };
		                })(callStackDepth);
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ }));
	        		}, false);
	                
	            case 'page-reload':
	            	return runSafe('page-reload', function() {
	        			shouldBreak = function() { return false; };
	        			breakpoints = {};
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ }));

		                setTimeout(function() {
    		                location.reload();
	        			}, 500);
	        		}, false);
	            	
	            case 'timeout':
	            	return true;
	            	
	            case 'getDatabaseTableNames':
	            	return runSafe('getDatabaseTableNames', function() {
	            		var db = databases[cmd.data.params.databaseId].database;
	            		db.transaction(function(tx) {
	            	        tx.executeSql("SELECT name FROM sqlite_master WHERE type = 'table'", [], function(tx,rs) {
	            	        	var tableNames = [];
	            	        	for (var i=0; i < rs.rows.length; i++) {
	            	        		var row = rs.rows.item(i);
	            	        		if (row.name == '__WebKitDatabaseInfoTable__') {
	            	        			continue;
	            	        		}
	            	        		tableNames.push(row.name);
	            	        	}
	    		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ tableNames:tableNames}));
	            	        });
	            	    });
	            			
	        		}, true);
	            	
	            case 'executeSQL':
	            	return runSafe('executeSQL', function() {
	            		var db = databases[cmd.data.params.databaseId].database;
	            		
	            		db.transaction(function(tx) {
	            	        tx.executeSql(cmd.data.params.query, [], function(tx,rs) {
	            	        	
	            	        	var colNames = [];
	            	        	var values = [];
	            	        	for (var ri=0; ri < rs.rows.length; ri++) {
	            	        		var row = rs.rows.item(ri);
	            	        		
            	        			for (var col in row) {
            	        				// extract header names for first column
    	            	        		if (ri == 0) {
   	            	        				colNames.push(col);
    	            	        		}
    	            	        		// extract row values
	            	        			values.push(row[col]);
	            	        		}
	            	        	}
	            	        	
	    		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ columnNames:colNames, values:values}));
	            	        });
	            	        
	            	    });
	            			
	        		}, true);
	            	
	            case 'Database.enable':
            		return runSafe('Database.enable', function() {
            			
            			databases.forEach(function(db) {
                			sendToDebugService('Database.addDatabase', {database:{id:db.id,domain:db.domain,name:db.name, version:db.version}});
            			});
            			
            		}, true);
	            
	            case 'ClientConnected':
	            	return true;
	            	
	            	
	        	default:
	        		console.warn('JsHybugger unknown command received:' + cmd.command);
	        }
		}
    }
    
    /**
     * Handles "getResourceTree" messages and send back result to debugger client.
	 * @param {object} cmd message from debug server
     */
    function getResourceTree(cmd) {
    
    	var prot = 'content://jsHybugger.org/';
    	var result = {
    		frameTree : {
    			frame : {
    				id : '3130.1',
    				url : document.location.href.indexOf(prot) == 0 ? document.location.href.substr(prot.length) : document.location.href,
    				loaderId: '3130.2',
    				securityOrigin : document.location.origin,
    				mimeType : 'text/html'
    			},
    			resources : []
    		}
    	};
    	
    	// get all script resources
    	var scripts = document.getElementsByTagName("script"); 
    	for (var i = 0; i < scripts.length; i++) {
    		var src = scripts[i].src;
    		if (src.indexOf('jshybugger.js')>=0) {
    			continue;
    		}
    		result.frameTree.resources.push({
    			// remove content provider
    			url : src.indexOf(prot) == 0 ? src.substr(prot.length) : src,
    			type : 'Script',
    			mimeType : 'text/x-js'
    		});
    	}
		
    	// get all image resources
    	var imgs = document.getElementsByTagName("img"); 
    	for (var i = 0; i < imgs.length; i++) {
    		var src = imgs[i].src;
    		var mimeType='image/png';
    		
    		if (src.indexOf("data:")>=0) {
    			mimeType = src.substring(src.indexOf(":")+1, src.indexOf(";")); 
    			
    		} else {
    			
    			mimeType = 'image/' + src.substr(src.lastIndexOf(".")+1); 
    		}
    		
    		result.frameTree.resources.push({
    			// remove content provider
    			url : src.indexOf(prot) == 0 ? src.substr(prot.length) : src,
    			type : 'Image',
    			mimeType : mimeType
    		});
    	}

    	// get all stylesheet resources
    	/* disabled - not supported
    	var styles = document.getElementsByTagName("link"); 
    	for (var i = 0; i < styles.length; i++) {
    		var src = styles[i].href;
    		if (src && styles[i].rel == 'stylesheet') {
	    		result.frameTree.resources.push({
	    			// remove content provider
	    			url : src.indexOf(prot) == 0 ? src.substr(prot.length) : src,
	    			type : 'Stylesheet',
	    			mimeType : 'text/css'
	    		});
    		}
    	}
        */
    	return result;
    }
    
    /**
     * Handles "getProperties" messages and send back result to debugger client.
	 * @param {object} cmd message from debug server
     */
    function getProperties(cmd) {
		
		var objectParams = cmd.data.objectId.split(":");
		var results = [];
		
		var stack = objectParams[0] === 'stack' ? callStack[objectParams[1]] : undefined;
		
		if (stack && (objectParams.length == 2)) {
			var varnames = stack && stack.varnames ? stack.varnames.split(",") : [];
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
						result.value.description = expr.constructor && expr.constructor.name ? expr.constructor.name : 'object';
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
			var props = objName ? objName.split('.') : [];

			if (!stack) {
				props = objectParams[1].split('.');
				obj = globalWatches[props[0]];
			} else if (objName.indexOf('this') == 0) {
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
				
				var result = {};
				
				result.value = {
			        type:oType,
			        description: ""
			    };
				if (oVal && oType == 'object') {
					result.value.objectId=cmd.data.objectId + "." + expr;
					result.value.description = oVal.constructor && oVal.constructor.name ? oVal.constructor.name : 'object';
				} else if (oType == 'function') {
					result.value.description = oVal ? oVal.toString() : 'function';
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
        
        JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ result : results }));
    }
    
    /**
     * Evaluates an expression in the given scope.
	 * @param {object} evalScopeFunc scope function for resolving variables on call stack
	 * @param {object} cmd message from debug server
     */
	function doEval(stack, cmd) {

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
            			response.description = evalResult.constructor ? evalResult.constructor.name : 'object';
            		} else {
            			response.description = "" + evalResult;
            		}
            	}
            } else {
            	response.type = typeof(evalResult);

            	if (params.returnByValue) {
            		response.description = response.value = evalResult;
            	} else {
                	var exprID = "ID" + new Date().getTime();
                	globalWatches[exprID] = evalResult ;
            		
            		response.objectId = "global:" + exprID;
            		if (response.type == 'object') {
            			response.description = evalResult.constructor ? evalResult.constructor.name : 'object';
            		} else {
            			response.description = "" + evalResult;
            		}
            	}
            }
        } catch (ex) {
            evalResult = ex.toString();
        }  
        JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe(response));
    }
    
	function stringifySafe(obj){
	    var printedObjects = [];
	    var printedObjectKeys = [];
	    var lastKey, lastVal;

	    function printOnceReplacer(key, value){
	    	lastKey = key;
	    	lastVal = value;
	    	
	        var printedObjIndex = false;
	        
	        printedObjects.forEach(function(obj, index){
	            if(obj===value){
	                printedObjIndex = index;
	            }
	        });

	        if(printedObjIndex && typeof(value)=="object"){
	            return "(see object with key " + printedObjectKeys[printedObjIndex] + ")";
	        } else {
	        	try {
	        		// HTMLInputElement will be not serializable
		        	if ((typeof(value)=="object") && value.constructor && value.constructor.name == "HTMLInputElement") {
		        		return null;
		        	}
		            var qualifiedKey = key || "(empty key)";
		            printedObjects.push(value);
		            printedObjectKeys.push(qualifiedKey);
	                return value;
	        	} catch (er) {
	        		return null;
	        	}
	        }
	    }
	    return JSON.stringify(obj, printOnceReplacer);
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
    function runSafe(name, fctn, retVal) {
        try {
        	fctn();
        } catch (ex) {
        	var str ="";
        	for (i in ex) {
        		str += i + ",";
        	}
            return console.log("runSafe failed for: " + name + ", "+ ex);
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
    
    function getDOMStorageItems(storage) {
    	var items = [];
    	for (key in storage) {
    		// JSHybugger is the prefix for the overwritten storge methods
    		if (key != null && key.indexOf('JsHybugger') != 0) {
    			items.push([ key, storage[key]]);
    		}
    	}
    	
    	return items;
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

	// intercept setItems, removeItems, clear method calls and notify jsHybugger
	if (Storage) {
		Storage.prototype.JsHybugger_setItem = Storage.prototype.setItem;
		Storage.prototype.setItem = function(key,value) {
			var item = this.getItem(key);
			this.JsHybugger_setItem(key,value);
			if (item == undefined) {
				sendToDebugService('DOMStorage.domStorageItemAdded', { storageId : { isLocalStorage : this == localStorage}, key : key, newValue : value});
			} else {
				sendToDebugService('DOMStorage.domStorageItemUpdated', { storageId : { isLocalStorage : this == localStorage}, key : key, oldValue : item, newValue : value});
			}
		};

		Storage.prototype.JsHybugger_removeItem = Storage.prototype.removeItem;
		Storage.prototype.removeItem = function(key) {
			this.JsHybugger_removeItem(key);
			sendToDebugService('DOMStorage.domStorageItemRemoved', { storageId : { isLocalStorage : this == localStorage}, key : key});
		};

		Storage.prototype.JsHybugger_clear = Storage.prototype.clear;
		Storage.prototype.clear = function() {
			this.JsHybugger_clear();
			sendToDebugService('DOMStorage.domStorageItemsCleared', { storageId : { isLocalStorage : this == localStorage}});
		};
	}
	
	// intercept openDatabase method calls and notify jsHybugger
	var addWebSQLDatabaseInfo = function(db,name,version,description) {
		console.log("addWebSQLDatabaseInfo() called: " + name);
		databases.push({database:db, id:new String(databases.length),domain:location.hostname,name:name, version:version});
		sendToDebugService('Database.addDatabase', {database:{id:new String(databases.length-1),domain:location.hostname,name:name, version:version}});
	};
	if (window.openDatabase) {
		window.JsHybugger_openDatabase = window.openDatabase;
		window.openDatabase = function(name,version,description,size,cb) {
			var db = window.JsHybugger_openDatabase(name,version,description,size,cb);
			addWebSQLDatabaseInfo(db,name,version,description,size,cb);
			return db;
		};
	}
	// watch set actions on openDatabase and rebind jsHybugger
	window.__defineSetter__("openDatabase", function(openFctn){
		//console.log("window.openDatabase setter: " + openFctn.toString());
		this.JsHybugger_openDatabase = function(name,version,description,size,cb) {
			var db = openFctn(name,version,description,size,cb);
			addWebSQLDatabaseInfo(db,name,version,description,size,cb);
			return db;
		};
    });
	window.__defineGetter__("openDatabase", function(){
		//console.log("window.openDatabase getter: " + (this.JsHybugger_openDatabase ? this.JsHybugger_openDatabase : "not set"));
		return this.JsHybugger_openDatabase;
    });

    /*
     * ---- begin public API functions
     */ 
    
    /**
     * Send 'GlobalPageLoaded' message to debug server message handlers.
     */
    function pageLoaded() {
        
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
    	
    	// process messages here to make sure all breakpoints are set  
    	processMessages(false);
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
    	processMessages : processMessages,
    	addWebSQLDatabaseInfo : addWebSQLDatabaseInfo
    }

})();
}
