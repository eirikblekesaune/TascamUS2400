TascamUS2400 {
	var <device;
	var <listenFuncs, <syncFuncs; //TEMP getter
	var <controlDescriptions;

	//This class expects the Tascam US-2400 to run in a 'transparent' mode.
	//To set the device to this mode hold the master 'SEL' and 'CHAN' keys pressed,
	//when turning on the device. The 'CHAN' button should blink three times to confirm
	//that the mode has been set.

	*new{|deviceName, portName|
		^super.new.init(deviceName, portName);
	}

	init{|deviceName, portName|
		device = MIDIDevice(deviceName, portName, deviceName, portName, "US-2400");
		controlDescriptions = VTMOrderedIdentityDictionary.new;
		this.initMappings;
	}

	initMappings{
		var makeFaderSyncFunction;
		var makeEncoderSyncFunction;
		var makeButtonSyncFunction;
		makeFaderSyncFunction = {|comp|
			{|cc, vv| cc.value_(vv).refresh;};
		};
		makeEncoderSyncFunction = {|comp|
			{| comp, val, mode = \dot, centerPoint = false|
				fork{
					var ringVal;
					comp.value_(val);
					ringVal = (comp.spec.unmap(comp.value) * 15).asInteger;
					ringVal = switch(mode, 
						\dot, {ringVal.bitOr(0x00)},
						\cut, {ringVal.bitOr(0x10)},
						\width, {ringVal.bitOr(0x20)},
						\spread, {ringVal.bitOr(0x30)}
					);
					if(centerPoint, {
						ringVal = ringVal.bitOr(0x60);
					});
					if(comp.syncCacheValue.isNil or: {ringVal != comp.syncCacheValue}, {
						comp.midiOut.control(comp.chan, comp.number, ringVal);
						comp.syncCacheValue = ringVal;
					});
				}
			};
		};
		makeButtonSyncFunction = {
			{|comp, val, blinking=false|
				var ledVal;
				ledVal = val.booleanValue.asInteger;
				if(blinking.not, {
					ledVal = ledVal << 1;
				});
				comp.midiOut.control(comp.chan, comp.number, ledVal);
				comp.syncCacheValue = ledVal;
			}
		};
		24.do({|i|
			var num;
			var slotNum = (i div: 8) + 1;
			var stripeNum = (i % 8) + 1;
			var compName = "slot_%_fader_%".format(slotNum, stripeNum).asSymbol; 
			var comp;
			//fader
			device.addComponent(
				compName: compName, chan: 0, number: i, msgType: \control14,
				spec: ControlSpec(0, 16368)
			);
			comp = device.components[compName];
			comp.addUniqueMethod(\setFader, makeFaderSyncFunction.value(comp));
			controlDescriptions.put(compName, (
				mode: \attribute,
				minVal: 0, maxVal: 16368, type: \integer, clipmode: \both, defaultValue: 0
			));

			//encoder
			compName = "slot_%_encoder_%".format(slotNum, stripeNum).asSymbol; 
			device.addComponent(
				compName: compName, chan: 0, number: i+64, msgType: \increment,
				spec: ControlSpec(0, 1023)
			);
			comp = device.components[compName];
			comp.addUniqueMethod(\setEncoderRing, makeEncoderSyncFunction.value(comp));
			comp.syncFunction = {|c|
				c.setEncoderRing(c.value, centerPoint: c.value == 0);
			};
			controlDescriptions.put(compName, (
				mode: \attribute,
				minVal: 0, maxVal: 1023, type: \integer, clipmode: \both, defaultValue: 0
			));

			//buttons
			[\faderTouch, \select, \solo, \mute].do({|buttonType, j|
				var ctrlNum = (i * 4) + j;
				var comp;
				compName = "slot_%_%_%".format(
					slotNum, buttonType, stripeNum
				).asSymbol; 
				device.addComponent(
					compName: compName, chan: 1, number: ctrlNum
				);
				comp = device.components[compName];
				comp.addUniqueMethod('setLED', makeButtonSyncFunction.value(comp));
				controlDescriptions.put("%_LED".format(compName).asSymbol, (
					mode: \attribute, type: \boolean, defaultValue: false,
					action: {|c|
						"Button LED action '%' value: '%'".format(compName, c.value).postln;
						comp.setLED(c.value);
					}
				));
				controlDescriptions.put(compName,
						( mode: \signal, type: \string, enum: [\pressed, \released], restrictToEnum: true)
					);
				});
		});
		//master section mappings
		{
			var comp;
			//master fader
			device.addComponent(
				compName: 'master_fader', chan: 0, number: 24, msgType: \control14,
				spec: ControlSpec(0, 16368)
			);
			device.components['master_fader'].addUniqueMethod(
				\setFader, makeFaderSyncFunction.value()
			);
			controlDescriptions.put('master_fader', (
				minVal: 0, maxVal: 16368, type: \integer, clipmode: \both, defaultValue: 0
			));
			//master fader touch
			device.addComponent( compName: 'master_faderTouch', chan: 2, number: 0);
			device.components['master_faderTouch'].addUniqueMethod(
				'setLED', makeButtonSyncFunction.value()
			);

			[
				(name: \flip, chan: 1, ctrlNum: 99),
				(name: \clr_solo, chan: 1, ctrlNum: 98),
				(name: \select, chan: 1, ctrlNum: 97),
				(name: \chan, chan: 1, ctrlNum: 100),
				(name: \pan, chan: 1, ctrlNum: 108),
				(name: \aux1, chan: 1, ctrlNum: 101),
				(name: \aux2, chan: 1, ctrlNum: 102),
				(name: \aux3, chan: 1, ctrlNum: 103),
				(name: \aux4, chan: 1, ctrlNum: 104),
				(name: \aux5, chan: 1, ctrlNum: 105),
				(name: \aux6, chan: 1, ctrlNum: 106),
				(name: \void, chan: 1, ctrlNum: 109),
				(name: \null, chan: 1, ctrlNum: 110),
				(name: \scrub, chan: 1, ctrlNum: 111),
				(name: \bank_minus, chan: 1, ctrlNum: 112),
				(name: \bank_plus, chan: 1, ctrlNum: 113),
				(name: \in, chan: 1, ctrlNum: 114),
				(name: \out, chan: 1, ctrlNum: 115),
				(name: \shift, chan: 1, ctrlNum: 116),
				(name: \rewind, chan: 1, ctrlNum: 117),
				(name: \forward, chan: 1, ctrlNum: 118),
				(name: \stop, chan: 1, ctrlNum: 119),
				(name: \play, chan: 1, ctrlNum: 120),
				(name: \record, chan: 1, ctrlNum: 121)
			].do({|item|
				var compName = "master_%".format(item[\name]).asSymbol;
				device.addComponent(
					compName: compName, chan: item[\chan], number: item[\ctrlNum]
				);
				device.components[compName].addUniqueMethod(\setLED, makeButtonSyncFunction.value());
				controlDescriptions.put(compName, (
					minVal: 0, maxVal: 1, type: \integer, clipmode: \both, defaultValue: 0,
					enum: [\released, \pressed]
				));
			});

			device.addComponent(
				compName: 'master_jog_wheel', chan:  0, number: 60,
				msgType: \increment
			);
			device.addComponent( compName: 'master_joystick_x', chan: 14, number: 90);
			device.addComponent( compName: 'master_joystick_y', chan: 14, number: 91);
		}.value;
	}

	free{
		device.free;
	}
}

