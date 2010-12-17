#!/bin/sh

rm -rf sf
mkdir sf
svn checkout https://p2pvpn.svn.sourceforge.net/svnroot/p2pvpn sf
rm -rf $(find . -name .svn)

rm -rf $1
mkdir $1
cp -r sf/p2pvpn/trunk/ $1
mv $1/trunk $1/p2pvpn
cp -r sf/tapLinux/trunk/ $1
mv $1/trunk $1/tapLinux
cp -r sf/tapWindows/trunk/ $1
mv $1/trunk $1/tapWindows
cp sf/COPYING* $1
tar cvfz $1.tar.gz $1

