TascamUS2400 {
	var slots;
	var page;
	var <inPorts, <outPorts;
	var <listenFuncs, <syncFuncs; //TEMP getter

	*new{|deviceName, portNameA, portNameB, portNameC|
		^super.new.init(deviceName, portNameA, portNameB, portNameC);
	}

	init{|deviceName, portNameA, portNameB, portNameC|
		inPorts = IdentityDictionary.new;
		outPorts = IdentityDictionary.new;
		try{
			inPorts[\A] = MIDIIn.findPort(deviceName, portNameA);
			inPorts[\B] = MIDIIn.findPort(deviceName, portNameB);
			inPorts[\C] = MIDIIn.findPort(deviceName, portNameC);

			outPorts[\A] = MIDIOut.newByName(deviceName, portNameA);
			outPorts[\B] = MIDIOut.newByName(deviceName, portNameB);
			outPorts[\C] = MIDIOut.newByName(deviceName, portNameC);

			if(outPorts.keys != Set[\A, \B, \C] and: {inPorts.keys != Set[\A, \B, \C]}, {
				Error().throw;
			});

			if(thisProcess.platform.name == \linux, {
				outPorts.keysValuesDo({|outPortKey, outPort|
					var outIndex;
					outIndex = MIDIClient.destinations.detectIndex({|source|
						source.uid == outPort.uid;
					});
					if(outIndex.notNil, {
						outPort.connect(outIndex);
					}, {
						"Could not connect MIDIOut for % to virtual source: %".format(
							outPortKey, outPort
						).warn;
					});
				});
			});
		} {|err|
			err.errorString.postln;
			inPorts.keys.postln;
			outPorts.keys.postln;
			Error("Did not find some of the ports for Tascam US2400").throw;
		};
		this.initMappings;
	}

	initMappings{
		var mappingsList = this.class.prMappings;
		listenFuncs = IdentityDictionary.new();
		syncFuncs = IdentityDictionary.new();
		mappingsList.do({|sectionMapping|
			var sectionKey, sectionData;
			sectionKey = sectionMapping.key;
			sectionData = sectionMapping.value;
			sectionData.do({|elementTypeMappings|
				var elementTypeKey = elementTypeMappings.key;
				var elementTypeData = elementTypeMappings.value;
				elementTypeData.do({|elementMappings|
					var elementKey = elementMappings.key;
					var elementData = elementMappings.value.as(IdentityDictionary);
					var elementDictKey = "%_%_%".format(sectionKey, elementTypeKey, elementKey).asSymbol;
					"Mapping: %".format(elementDictKey, elementData).postln;

					case
					{elementTypeKey == \fader} {
						var cmdType = elementData[\cmdType];
						var chan = elementData[\chan];
						var inPort = inPorts[ elementData[\inPort] ];
						var outPort = outPorts[ elementData[\outPort] ];
						listenFuncs.put(elementDictKey,
							MIDIFunc.bend({|...args|
								"%: %".format(elementDictKey, args).postln;
							}, chan: chan, srcID: inPort.uid);
						);
						syncFuncs.put(elementDictKey, {|val|
							outPort.bend(chan, val.clip(0, 16368));
						});
					}
					{[\faderTouch, \faderRelease].includes(elementTypeKey)} {
						var cmdType = elementData[\cmdType];
						var chan = elementData[\chan];
						var noteNum = elementData[\noteNum];
						var inPort = inPorts[ elementData[\inPort] ];
						listenFuncs.put(elementDictKey, 
							MIDIFunc.perform(cmdType, {|args|
								"%: %".format(elementDictKey, args).postln;
							}, noteNum: noteNum, chan: chan, srcID: inPort.uid);
						);
					}
					{elementTypeKey == \encoder} {
						var cmdType = elementData[\cmdType];
						var ctrlNum = elementData[\ctrlNum];
						var chan = elementData[\chan];
						var inPort = inPorts[ elementData[\inPort] ];
						listenFuncs.put(elementDictKey,
							MIDIFunc.perform(cmdType, {|...args|
								"%: %".format(elementDictKey, args).postln;
							}, ccNum: ctrlNum, chan: chan, srcID: inPort.uid);
						);
					}
					{
						[
							\mute_pressed, \mute_released,
							\solo_pressed, \solo_released,
							\select_pressed, \select_released
						].includes(elementTypeKey)
					} {
						var cmdType = elementData[\cmdType];
						var noteNum = elementData[\noteNum];
						var chan = elementData[\chan];
						var inPort = inPorts[ elementData[\inPort] ];
						listenFuncs.put(elementDictKey,
							MIDIFunc.perform(cmdType, {|...args|
								"%: %".format(elementDictKey, args).postln;
							}, noteNum: noteNum, chan: chan, srcID: inPort.uid)
						);
					}
					{
						[
							\mute_LED, \solo_LED, \select_LED
						].includes(elementTypeKey)
					} {
						var cmdType = elementData[\cmdType];
						var noteNum = elementData[\noteNum];
						var chan = elementData[\chan];
						var outPort = outPorts[ elementData[\outPort] ];
						var buttonType = elementData[\buttonType];
						var buttonNum = elementData[\buttonNum];
						syncFuncs.put(elementDictKey, {|val, blinking = false|
							var buttonTypeCode = (
								mute: 2, select: 3, solo: 1
							).at(buttonType);
							var noteNumByte = (buttonTypeCode << 3).bitOr(buttonNum - 1);
							var valueByte;
							if(val.booleanValue, {
								valueByte = 2;
							}, {
								valueByte = 0;
							});
							if(blinking, {
								valueByte = valueByte >> 1;
							});
							outPort.noteOn(chan, noteNumByte, valueByte);
						});
					}
				});
			});
		});
	}

	mapPageToSlot{|pageKey, slotNum|

	}

	*prMappings{
		var result;
		result = [\A, \B, \C].collect({|portKey, i|
			var sectionKey = "slot_%".format(i + 1).asSymbol;
			sectionKey -> [
				\fader -> [
					1 -> [ cmdType: \bend, chan: 0, inPort: portKey, outPort: portKey ],
					2 -> [ cmdType: \bend, chan: 1, inPort: portKey, outPort: portKey ],
					3 -> [ cmdType: \bend, chan: 2, inPort: portKey, outPort: portKey ],
					4 -> [ cmdType: \bend, chan: 3, inPort: portKey, outPort: portKey ],
					5 -> [ cmdType: \bend, chan: 4, inPort: portKey, outPort: portKey ],
					6 -> [ cmdType: \bend, chan: 5, inPort: portKey, outPort: portKey ],
					7 -> [ cmdType: \bend, chan: 6, inPort: portKey, outPort: portKey ],
					8 -> [ cmdType: \bend, chan: 7, inPort: portKey, outPort: portKey ],
				],
				\faderTouch -> (104..111).collect({|noteNum, i|
					var num = i + 1;
					num -> [cmdType: \noteOn, chan: 0, inPort: portKey, noteNum: noteNum]
				}),
				\faderRelease -> (104..111).collect({|noteNum, i|
					var num = i + 1;
					num -> [cmdType: \noteOff, chan: 0, inPort: portKey, noteNum: noteNum]
				}),
				\encoder -> (16..23).collect({|ctrlNum, i|
					var num = i + 1;
					num -> [cmdType: \cc, chan: 0, ctrlNum: ctrlNum, inPort: portKey ]
				}),
				[
					[\mute, (16..23)], [\solo, (8..15)], [\select, (24..31)]
				].collect({|buttonMappings|
					var buttonType = buttonMappings.first;
					var buttonNoteNums = buttonMappings.last;
					[
						"%_pressed".format(buttonType).asSymbol -> buttonNoteNums.collect({|noteNum, i|
							var num = i + 1;
							num -> [cmdType: \noteOn, chan: 0, inPort: portKey, noteNum: noteNum]
						}),
						"%_released".format(buttonType).asSymbol -> buttonNoteNums.collect({|noteNum, i|
							var num = i + 1;
							num  -> [cmdType: \noteOff, chan: 0, inPort: portKey, noteNum: noteNum]
						}),
						"%_LED".format(buttonType).asSymbol -> buttonNoteNums.collect({|noteNum, i|
							var num = i + 1;
							num -> [cmdType: \noteOn, chan: 0, outPort: portKey, noteNum: noteNum, buttonType: buttonType, buttonNum: num]
						})
					];
				}).flatten
			].flatten;
		});
		^result;
	}
}
