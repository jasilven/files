system=$(uname)

if [ $system = "Linux" ]
then
    sudo systemctl start postgresql
else
    postgres -D /usr/local/var/postgres/
fi
