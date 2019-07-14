system=$(uname)

if [ $system = "Linux" ]
then
    sudo systemctl stop postgresql
else
    pg_ctl -D /usr/local/var/postgres stop
fi


