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
	var THIS = this;
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
	var continueToLocation;
	var breakpointsActive = true;
	var pauseOnExceptionsState = 'none';
	var NOT_WHITESPACE_MATCHER = /[^\s]/;
	var FRAME_ID = new String(new Date().getTime() % 3600000);
	var PROTOCOL = 'content://jsHybugger.org/';
	
	
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
        
        ['info', 'log', 'warn', 'error', 'debug', 'trace','group','groupEnd','assert'].forEach(function(f) {
            var oldFunc = window.console[f];
            var levels = {
            	log : 'log',
            	warn : 'warning',
            	info : 'info', 
            	error : 'error',
            	debug : 'debug',
            	trace : 'log',
            	group : 'log',
            	groupEnd : 'log',
            	assert : 'error'
            };
            var types = {
            	info : 'log',	
            	log : 'log',	
            	warn : 'log',	
            	debug : 'log',	
            	error : 'log',	
            	trace : 'trace',
            	group : 'startGroup',
            	groupEnd : 'endGroup',
            	assert : 'assert'
            };
            
            window.console[f] = function() {
			
                var args = Array.prototype.slice.call(arguments);
            	
                /* Write to local console first */
                oldFunc && oldFunc.apply(window.console, args);

                // special handling for assert calls
                if (f == 'assert') {
            		if (args[0] === true) {
            			return;
        			}
        			// else remove first item 
        			args = args.splice(1,1);
            	}
                
                var parameters = [];
                for (var i=0; args && i<args.length; i++) {
                	var type = typeof(args[i]);
                	var arg = {
                		type : type
                	};
                	if (type == 'object') {
                		arg.description = arg.className = args[i].constructor &&  args[i].constructor.name ?  args[i].constructor.name : 'Object';
                		arg.objectId = "not_supported";
                		
                		arg.preview = { lossless : true,
                            overflow : false,
                            properties : [ ]
                        };
                		
                		for (var prop in args[i]) {
                			var propVal = args[i][prop];
                			var propType = typeof(propVal);
                			
                			arg.preview.properties.push({ 
                				name : prop,
                                type : propType == 'object' && propVal.constructor &&  propVal.constructor.name ? propVal.constructor.name : propType,
                                value : propVal
                            });
                		}
                		
                    } else {
                		arg.value = args[i]; 		
                    }

                	parameters.push(arg);
                }

                
                sendToDebugService('Console.messageAdded', { 
                	message : {
                		level: levels[f],
                		line : lastLine,
                		parameters : parameters,
                		repeatCount : 1,
                		source : "console-api",
                		stackTrace : getStacktrace(),
                		text : args && args.length>0 ? args[0] : '',
                		type : types[f],
                		url : lastFile
                	}
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
	        		
	        	case 'setPauseOnExceptions':
	        		return runSafe('setPauseOnExceptions', function() {
	        			pauseOnExceptionsState = cmd.data.params.state;
	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({}));
	        		}, true);

	        	case 'setBreakpointsActive':
	        		
	        		return runSafe('setBreakpointsActive', function() {
	        			breakpointsActive = cmd.data.params.active;
	        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({}));
	        		}, true);
	        		
	        	case 'callFunctionOn':
	        		return runSafe('callFunctionOn', function() {
	        			var obj = getObject(cmd.data.params.objectId);
	        			var fctn = new Function('return (' + cmd.data.params.functionDeclaration + ').apply(this,arguments)');
	        			var val = obj && fctn ? fctn.apply(obj, cmd.data.params.arguments) : {};
	        			var response = {
        					result : {
        						result : { 
        							type : typeof(val),
        							value : val
        						}
        					}
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
	        			
	        			processStylesheets();
	        			
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
	        		        		
	            case 'continue-to':
	            	
	        		return runSafe('continue-to',function() {
	        			continueToLocation = {
	        				file : cmd.data.url,
	        				line : cmd.data.lineNumber
	        			};
	        			
		                JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe({ }));
	        		}, false);
	        			
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
	        		var fctn = eval(cmd.command);
	        		if (fctn) {
		        		return runSafe(cmd.command, function() {
		        			var rVal = fctn(cmd.data.params);
		        			JsHybuggerNI.sendReplyToDebugService(cmd.replyId, stringifySafe(rVal));
		        		}, true);
	        		} else {	        		
	        			console.warn('JsHybugger unknown command received:' + cmd.command);
	        		}
	        }
		}
    }

    /**
     * Process "getComputedStyleForNode" message from debug client.
     */
    function getComputedStyleForNode(params) {
    	var nodeId = params.nodeId;
		var node = getNodeById(nodeId);
		var computedStyle = [];
		
		if (node) {
	    	var styles = window.getComputedStyle(node, '');
	    	
	    	for (prop in styles) {
	    		if (!isNaN(prop)) {
	    			computedStyle.push({
		    			name : styles[prop],
		    			value : styles.getPropertyValue(styles[prop])
		    		});
	    		}
	    	}
		}
		
    	return { "computedStyle":computedStyle };
    }
    
    /**
     * Process "getMatchedStylesForNode" message from debug client.
     */
    function getMatchedStylesForNode(params) {
    	var nodeId = params.nodeId;
		var node = getNodeById(nodeId);
		var matchedCSSRules = [];
		
		if (node) {
			var matchedRules = window.getMatchedCSSRules(node, '');
			for (ruleIdx=0; matchedRules && ruleIdx < matchedRules.length; ruleIdx++) {
				var matchedRule = matchedRules[ruleIdx];
				var styleSheetId = matchedRule.parentStyleSheet.styleSheetId;
				
				var sheetRules = matchedRule.parentStyleSheet.rules;
				for (var ordinal=0; ordinal < sheetRules.length; ordinal++) {
					if (sheetRules[ordinal] == matchedRule) {
						break;
					}
				}
				var rule = {
					selectorList : {
						selectors : [ matchedRule.selectorText ],
						text : matchedRule.selectorText
					},
					origin:"regular",
					style : {
						styleId:{
		                     styleSheetId:new String(styleSheetId),
		                     ordinal:ordinal
		                },
		                shorthandEntries:[],
		                cssProperties : getCSSStyleProperties(matchedRule.style),
		                cssText : matchedRule.cssText
					},
					ruleId:{
	                     styleSheetId:new String(styleSheetId),
	                     ordinal:ordinal
		            }
				}
				
				matchedCSSRules.push({
					rule : rule,
					matchingSelectors : [ 0 ]
				});
			}
		}
		
		return {
    	      matchedCSSRules: matchedCSSRules,
              pseudoElements:[],
              inherited:[]
    	};
    }
    
    /**
     * Convert CSSStyleDeclaration object to CSSStyle object properties from inspector.json
     * 
     * { "name": "styleId", "$ref": "CSSStyleId", "optional": true, "description": "The CSS style identifier (absent for attribute styles)." },
     * { "name": "cssProperties", "type": "array", "items": { "$ref": "CSSProperty" }, "description": "CSS properties in the style." },
     * { "name": "shorthandEntries", "type": "array", "items": { "$ref": "ShorthandEntry" }, "description": "Computed values for all shorthands found in the style." },
     * { "name": "cssText", "type": "string", "optional": true, "description": "Style declaration text (if available)." },
     * { "name": "range", "$ref": "SourceRange", "optional": true, "description": "Style declaration range in the enclosing stylesheet (if available)." },
     * { "name": "width", "type": "string", "optional": true, "description": "The effective \"width\" property value from this style." },
     * { "name": "height", "type": "string", "optional": true, "description": "The effective \"height\" property value from this style." }
     */
    function getCSSStyleProperties(style) { 
    	var cssProperties = [];
    	
		// save orignal css properties on first style access 
		if (!style.JsHybuggerProperties) {
			for (i=0; i<250; i++) {
				var name = style.item(i);
				if (!name) {
					break;
				}
				
				cssProperties.push({
					name : name,
					value : style.getPropertyValue(name),
					priority : style.getPropertyPriority(name),
					implicit : style.isPropertyImplicit(name),
					text : style.getPropertyCSSValue(name).cssText,
					status : 'active'
				});
			}

			style.JsHybuggerProperties = cssProperties;
			style.setProperty('resize','none');  // trick to keep the css rule bound to the element, if all other attributes are removed
			                                     // resize is not supported on android devices - so use this property 
			
		} else {
			cssProperties = style.JsHybuggerProperties;
		}
		
		return cssProperties;
	}

    
    /**
     * Process "setPropertyText" message from debug client.
     */
    function setPropertyText(params) {
    	console.log("setPropertyText: " + params.styleId.styleSheetId + ", isNaN: " + isNaN(params.styleId.styleSheetId));
    	var style = isNaN(params.styleId.styleSheetId) 
    					? getNodeById(params.styleId.styleSheetId.split(":")[1]).style
    					: document.styleSheets[params.styleId.styleSheetId].rules[params.styleId.ordinal].style;
        
		var attr = params.text.split(":");
		var name = attr[0];
		var value = attr[1].substr(0, attr[1].length -1);

		var prop = null;
		if (!params.overwrite) {  // new property
			
			style.setProperty(name, value, null);
			prop = {
				name : name,
				value : style.getPropertyValue(name),
				priority : style.getPropertyPriority(name),
				implicit : style.isPropertyImplicit(name),
				text : params.text,
				status : 'active'
			};

			style.JsHybuggerProperties.splice(params.propertyIndex, 0, prop);

		} else {
			prop = style.JsHybuggerProperties[params.propertyIndex];
			if (prop.status == 'active') {
				style.setProperty(name, value, null);
			}
			prop.value = value;
			prop.text = params.text;
		}

    	sendToDebugService("CSS.styleSheetChanged", { styleSheetId : params.styleId.styleSheetId});

		return {
	   	     style:{
				styleId:{
                     styleSheetId:new String(params.styleId.styleSheetId),
                     ordinal: params.styleId.ordinal
                },
     	         cssProperties: style ? getCSSStyleProperties(style) : [],
	   	         shorthandEntries:[],
	   	         width:"",
	   	         height:"",
	   	         cssText: style ? style.cssText : ''
	   	     }
		     /* attributesStyle : [] optional - not supported  */ 
	   	};
    }
    
    /**
     * Process "toggleProperty" message from debug client.
     */
    function toggleProperty(params) {
    	
    	var style = isNaN(params.styleId.styleSheetId)  // internal styles format: "node:<nodeid>"
    					? getNodeById(params.styleId.styleSheetId.split(":")[1]).style
    					: document.styleSheets[params.styleId.styleSheetId].rules[params.styleId.ordinal].style;
    
    	var prop = style.JsHybuggerProperties[params.propertyIndex];
    	if (params.disable) {
    		style.removeProperty(prop.name);
    		prop.status = 'disabled';
    	} else {
    		style.setProperty(prop.name, prop.value, prop.priority);
    		prop.status = 'active';
    	}

    	sendToDebugService("CSS.styleSheetChanged", { styleSheetId : params.styleId.styleSheetId});

		return {
	   	     style:{
				styleId:{
                     styleSheetId:new String(params.styleId.styleSheetId),
                     ordinal: params.styleId.ordinal
                },
     	         cssProperties: style ? getCSSStyleProperties(style) : [],
	   	         shorthandEntries:[],
	   	         width:"",
	   	         height:"",
	   	         cssText: style ? style.cssText : ''
	   	     }
		     /* attributesStyle : [] optional - not supported  */ 
	   	};
    }

    /**
     * Process "getInlineStylesForNode" message from debug client.
     */
    function getInlineStylesForNode(params) {
    	var nodeId = params.nodeId;
		var node = getNodeById(nodeId);

		return {
    	     inlineStyle:{
 				 styleId:{
                    styleSheetId:'node:' + nodeId,
                    ordinal: 0
                 },
    	         cssProperties: node ? getCSSStyleProperties(node.style) : [],
    	         shorthandEntries:[],
    	         width:"",
    	         height:"",
    	         cssText: node ? node.style.cssText : ''
    	     }
		     /* attributesStyle : [] optional - not supported  */ 
    	};
    }
    
    /**
     * Process "requestChildNodes" message from debug client.
     */
    function requestChildNodes(params) {

    	var nodeId = params.nodeId;
		var node = getNodeById(nodeId);
		var nodes = node ? getNode(node, 0, 1).children : [];
		
		return {
			parentId : nodeId,
			nodes : nodes 
		};
	}

    /**
     * Return DOM node for given jsHybugger ID.
     */
	function getNodeById(nodeId) {
		return document.querySelector("[jsHybuggerId='" + nodeId + "']");    	
    }
    
    /**
     * Process "removeNode" message from debug client.
     */
    function removeNode(params) {
    	var nodeId = params.nodeId;
		var node = getNodeById(nodeId);
		if (node.parentNode) {
			node.parentNode.removeChild(node);
		}
    	return {
    		parentNodeId : node.parentNode ? node.parentNode.getAttribute("jshybuggerid") || 1 : 1,
    		nodeId : nodeId
    	};
    }
    
    /**
     * Returns an array of supported css attributes in the following format. 
     * [ { name : "background-color"}, { name : "border-color }, ... ]
     */
    function getSupportedCSSProperties() {
    
    	var cssProps = [];
    	var styles = window.getComputedStyle(document.body, '');
    	
    	for (prop in styles) {
    		if (!isNaN(prop)) {
	    		cssProps.push({
	    			name : styles[prop]
	    		});
    		}
    	}

    	return { cssProperties : cssProps };
    }
    
    /**
     * Process "getDocument" message from debug client.
     */
    function getDocument() {
    	
    	var root = getNode(document, 0, 2);
    	root.documentURL = document.documentURI;
    	root.baseURL = document.baseURI;
    	root.xmlVersion = document.xmlVersion;
    	
    	return { root : root };
    }

    var jsHybuggerNodeId=1;
    function getNode(node, level, maxLevel) {
    	//console.log("getNode: " + node ? node.nodeName +", " + node.nodeType : 'empty');
    	
    	var nodeId = node.getAttribute ? node.getAttribute("jsHybuggerId") : jsHybuggerNodeId++;
    	if (!nodeId) {
    		nodeId = jsHybuggerNodeId++;
    		node.setAttribute("jsHybuggerId", nodeId);
    	}
    	
    	var nodeData = {
    		nodeId : parseInt(nodeId),	
    		nodeType : node.nodeType,
    		nodeName : node.nodeType != 3 ? node.nodeName : '',
    		localName : node.localName,
    		nodeValue : node.nodeValue,
    		attributes : []
    	};
    	
    	// add all node attributes to result
    	var attrLength = node.attributes ? node.attributes.length : 0;
    	for (i=0; i < attrLength; i++) {
    		var attr = node.attributes[i];
        	if (attr.nodeName == 'jshybuggerid') { // skip jshybugger properties
        		continue;
        	}
        	nodeData.attributes.push(attr.nodeName);
    		nodeData.attributes.push(attr.nodeValue);
    	}
    	
    	// add child nodes to result
    	nodeData.childNodeCount = node.childNodes.length;
    	if (level++ < maxLevel) {
    		nodeData.children = [];
	    	for (x=0; x < nodeData.childNodeCount; x++) {
	    		var newNode = node.childNodes[x];
	    		if (newNode.nodeType == 3) {  // skip whitespace nodes
	    			if (!NOT_WHITESPACE_MATCHER.test(newNode.nodeValue)) {
	    				continue;
	    			}
	    		}
    			nodeData.children.push(getNode(newNode, level, maxLevel));
	    	}
    	}
    	
    	return nodeData;
    }
    
    /**
     * Handles "getResourceTree" messages and send back result to debugger client.
	 * @param {object} cmd message from debug server
     */
    function getResourceTree(cmd) {
    
    	var result = {
    		frameTree : {
    			frame : {
    				id : FRAME_ID,
    				url : document.location.href.indexOf(PROTOCOL) == 0 ? document.location.href.substr(PROTOCOL.length) : document.location.href,
    				loaderId: FRAME_ID,
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
    			url : src.indexOf(PROTOCOL) == 0 ? src.substr(PROTOCOL.length) : src,
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
    			url : src.indexOf(PROTOCOL) == 0 ? src.substr(PROTOCOL.length) : src,
    			type : 'Image',
    			mimeType : mimeType
    		});
    	}

    	// get all stylesheet resources
    	/* disabled - not supported*/
    	var styles = document.getElementsByTagName("link"); 
    	for (var i = 0; i < styles.length; i++) {
    		var src = styles[i].href;
    		if (src && styles[i].rel == 'stylesheet') {
    			var url = src.indexOf(PROTOCOL) == 0 ? src.substr(PROTOCOL.length) : src;
	    		result.frameTree.resources.push({
	    			// remove content provider
	    			url : url,
	    			type : 'Stylesheet',
	    			mimeType : 'text/css'
	    		});
    		}
    	}
        
    	return result;
    }
    
    function processStylesheets() {
    	
    	var styles = document.styleSheets; 
    	for (var i = 0; i < styles.length; i++) {
    	    // assign stylesheet an ID for easier relation mapping
    		styles[i].styleSheetId=i;  
    		var href = styles[i].href ? styles[i].href : document.location.href;
    		
    		// strip content protocol
			href = href && href.indexOf(PROTOCOL) == 0 ? href.substr(PROTOCOL.length) : null;
    		sendToDebugService("CSS.styleSheetAdded", {
    			header:{
    		         styleSheetId:i,
    		         origin:"regular",
    		         disabled:styles[i].disabled,
    		         sourceURL:href,
    		         title:styles[i].title,
    		         frameId:FRAME_ID,
    		         isInline:styles[i].href==null,
    		         startLine:0,
    		         startColumn:0
    		      }
    		});
    	}
    }
    
    function getStacktrace() {
    	var stacktrace = [];
    	
    	if (callStack) {
    		for (var i=callStack.length-1; i>=0; i--) {
    			var stack = callStack[i];
//    			if(stack.file.indexOf("console.") < 0) {
    				
	    			stacktrace.push( {
	    				columnNumber : 0,
	    				functionName : stack.name,
	    				lineNumber : i==callStack.length-1 ? lastLine+1 : stack.line+2,
	    				url: stack.file
	    			});
//    			}
    		}
    	}
    	
        return stacktrace;
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
			var obj = getObject(cmd.data.objectId);
			
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
     * Returns the js object for a given object path.
     * @param {string} objectId object identifier i.e. global:id
     */
    function getObject(objectId) {
    	
		var objectParams = objectId.split(":");
		
		var stack = objectParams[0] === 'stack' ? callStack[objectParams[1]] : undefined;
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
		
		return obj;
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
    	// clear continue to location information on pause
    	continueToLocation=null;
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
		sendToDebugService('GlobalInitHybugger', { frameId : FRAME_ID  });
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
	}

	// watch set actions on openDatabase and rebind jsHybugger
	window.__defineSetter__("openDatabase", function(openFctn){
		window.JsHybugger_openDatabase = openFctn;
    });
	window.__defineGetter__("openDatabase", function(){
		var openDB = this.JsHybugger_openDatabase;
		if (!openDB) {
			return openDB;
		}
		
		return function(name,version,description,size,cb) {
			var db = openDB(name,version,description,size,cb);
			addWebSQLDatabaseInfo(db,name,version,description,size,cb);
			return db;
		};
    });

    /*
     * ---- begin public API functions
     */ 
    
    /**
     * Send 'GlobalPageLoaded' message to debug server message handlers.
     */
    function pageLoaded() {
        
		var cmd = sendToDebugService('GlobalPageLoaded', {  frameId : FRAME_ID  });
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
                           shouldBreak(callStackDepth) ||                      /* break on next (in|over|out) */
                           (continueToLocation && continueToLocation.file == file && continueToLocation.line == line);

        if (!isBreakpoint || !breakpointsActive) {
            return;
        }

        if (breakpoints[file][line] && breakpointsById[breakpoints[file][line]].condition) {
        	var cond = breakpointsById[breakpoints[file][line]].condition;
        	try {
	        	if (!callStack[callStackDepth-1].evalScope(cond)) {
	        		return;
	        	}
        	} catch (ex) {
        		console.error('invalid breakpoint condition: ' + ex);
        		return;
        	}
        }
        
    	sendDebuggerPaused('other', { });
    	processMessages(true);
    };
    
    /**
     * Used by the instrumented code to report thrown exceptions in the code.
     */
    function reportException(e) {
    	console.error(e.toString());
    	if (pauseOnExceptionsState != 'none') {
    		// none, all, uncaught - at the moment uncaught and all is the same
    		sendDebuggerPaused('exception', { description : e.toString() });
    	}
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
