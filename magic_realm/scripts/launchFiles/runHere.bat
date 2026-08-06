echo off
@start javaw -Duser.home="." -Xms1g -Xmx4g -cp mail.jar;activation.jar;RealmSpeak.jar com.robin.magic_realm.RealmSpeak.RealmSpeakFrame %1