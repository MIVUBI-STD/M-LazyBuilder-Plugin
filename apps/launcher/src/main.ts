import './styles/app.css';
import App from './App.svelte';
import { mount } from 'svelte';
import { installModalAccessibility } from './app/modalAccessibility';

mount(App, { target: document.getElementById('app')! });
installModalAccessibility();
