echo off
set J2D_D3D=false
@start javaw -Duser.home="." -Xms1g -Xmx4g -cp mail.jar;activation.jar;RealmSpeakFull.jar com.robin.magic_realm.RealmSpeak.RealmSpeakFrame %1