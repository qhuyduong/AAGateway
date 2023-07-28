# AAGateway
Proof of Concept Android app that converts Wired Android Auto to Wireless Android Auto.

## How does this work?
1. [Dummy HCD](https://github.com/torvalds/linux/blob/master/drivers/usb/gadget/udc/dummy_hcd.c) is used to create a software-emulated USB host controller.
2. [Android Accessory](https://lore.kernel.org/all/20201012111024.2259162-2-rickyniu@google.com/) gadget driver is used to create an [Android Open Accessory](https://source.android.com/docs/core/interaction/accessories/protocol)-capable device.
3. This app acts as a data relay between an Android phone and Android Accessory.
