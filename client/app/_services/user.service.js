import config from 'config';
import { authHeader, handleResponse } from '@/_helpers';

export const userService = {
    getAll,newUser
};

function getAll() {
    const requestOptions = { method: 'GET', headers: authHeader() };
    return fetch(`${config.apiUrl}/users`, requestOptions).then(handleResponse);
}

function newUser(username,password,firstName,lastName) {
    const requestOptions = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({username,password,firstName,lastName})
    };
    return fetch(`${config.apiUrl}/user`, requestOptions).then(handleResponse);
}