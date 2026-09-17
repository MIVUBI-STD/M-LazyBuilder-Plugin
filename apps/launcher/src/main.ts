import './styles/app.css';
import App from './App.svelte';
import { mount } from 'svelte';
import { installActivityAccessibility } from './app/activityAccessibility';

mount(App, { target: document.getElementById('app')! });
installActivityAccessibility();
