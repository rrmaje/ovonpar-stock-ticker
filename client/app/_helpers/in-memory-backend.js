
export function configureInMemoryBackend() {
    init();
    let realFetch = window.fetch;
    window.fetch = function (url, opts) {
        const isLoggedIn = opts.headers['Authorization'] === 'Bearer jwt-token';

        return new Promise((resolve, reject) => {
            // wrap in timeout to simulate server api call
            setTimeout(() => {
                // authenticate - public
                if (url.endsWith('/users/authenticate') && opts.method === 'POST') {
                    const params = JSON.parse(opts.body);
                    const users = JSON.parse(localStorage.getItem('users'));
                    const user = users.find(x => x.username === params.username && x.password === params.password);
                    if (!user) return error('Username or password is incorrect');
                    return ok({
                        id: user.id,
                        username: user.username,
                        token: 'jwt-token'
                    });
                }

                // get users - secure
                if (url.endsWith('/users') && opts.method === 'GET') {
                    if (!isLoggedIn) return unauthorised();
                    const users = JSON.parse(localStorage.getItem('users'));
                    return ok(users);
                }

                // add user
                if (url.endsWith('/user') && opts.method === 'POST') {
                    const params = JSON.parse(opts.body);
                    const users = JSON.parse(localStorage.getItem('users'));
                    const user = users.find(x => x.username === params.username);
                    if (user) return error('User exists');
                    users[users.length] = { id: users.length + 1, username: params.username, password: params.password }
                    localStorage.setItem('users', JSON.stringify(users));
                    return ok({})
                }

                // generate hash
                if (url.endsWith('/users/authenticate/genhash') && opts.method === 'POST') {
                    const params = JSON.parse(opts.body);
                    const users = JSON.parse(localStorage.getItem('users'));
                    const user = users.find(x => x.username === params.username);
                    if (!user) return error('User not found');
                    let genhash = makeid();
                    let gentimestamp = new Date().getTime() + 3600000;
                    let reset = { username: params.username, hash: genhash, timestamp: gentimestamp }
                    localStorage.setItem('reset', JSON.stringify(reset));
                    localStorage.setItem('users', JSON.stringify(users));
                    return ok({ genhash })
                }

                // authenticate with reset - public
                if (url.endsWith('/users/authenticate/reset') && opts.method === 'POST') {
                    const params = JSON.parse(opts.body);
                    const users = JSON.parse(localStorage.getItem('users'));
                    const user = users.find(x => x.username === params.username);
                    if (!user) return error('Username is incorrect');
                    const reset = localStorage.getItem('reset');
                    if (!reset) return error('Authentication token is incorrect');

                    const resetToken = JSON.parse(reset);
                    if (!resetToken || !(resetToken.username === user.username) || !(resetToken.hash === params.genhash)) return error('Authentication token is incorrect');
                    const timestamp = resetToken.timestamp;
                    if (new Date().getTime() > timestamp) return error('Authentication token is incorrect');

                    user.password = params.password;
                    localStorage.setItem('users', JSON.stringify(users));
                    return ok({
                        id: user.id,
                        username: user.username,
                        token: 'jwt-token'
                    });
                }

                // pass through any requests not handled above
                realFetch(url, opts).then(response => resolve(response));

                // private helper functions

                function ok(body) {
                    resolve({ ok: true, text: () => Promise.resolve(JSON.stringify(body)) })
                }

                function unauthorised() {
                    resolve({ status: 401, text: () => Promise.resolve(JSON.stringify({ message: 'Unauthorised' })) })
                }

                function error(message) {
                    resolve({ status: 400, text: () => Promise.resolve(JSON.stringify({ message })) })
                }
            }, 500);
        });
    }
}

function makeid() {
    var text = "";
    var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    for (var i = 0; i < 10; i++)
        text += possible.charAt(Math.floor(Math.random() * possible.length));

    return text;
}

function init() {
    var users = JSON.parse(localStorage.getItem('users'));
    if (!users) {
        users = [{ id: 1, username: 'test@foo.com', password: 'test' }];
        localStorage.setItem('users', JSON.stringify(users));
    }
}