// Script untuk menambahkan micro-interaksi dan animasi pada saat scroll

document.addEventListener('DOMContentLoaded', () => {
    // 1. Efek Fade-in saat elemen masuk layar (Intersection Observer)
    const faders = document.querySelectorAll('.fade-in');
    
    const appearOptions = {
        threshold: 0.15,
        rootMargin: "0px 0px -50px 0px"
    };

    const appearOnScroll = new IntersectionObserver(function(entries, observer) {
        entries.forEach(entry => {
            if (!entry.isIntersecting) {
                return;
            } else {
                // Tambahkan class atau style untuk men-trigger animasi
                entry.target.style.opacity = '1';
                entry.target.style.transform = 'translateY(0)';
                // Hentikan observasi setelah animasi berjalan sekali
                observer.unobserve(entry.target);
            }
        });
    }, appearOptions);

    faders.forEach(fader => {
        // Set state awal
        fader.style.opacity = '0';
        fader.style.transform = 'translateY(20px)';
        fader.style.transition = 'opacity 0.8s ease-out, transform 0.8s ease-out';
        
        // Cek property delay berdasarkan class delay-1, delay-2, dst
        if (fader.classList.contains('delay-1')) {
            fader.style.transitionDelay = '0.2s';
        } else if (fader.classList.contains('delay-2')) {
            fader.style.transitionDelay = '0.4s';
        } else if (fader.classList.contains('delay-3')) {
            fader.style.transitionDelay = '0.6s';
        }

        appearOnScroll.observe(fader);
    });

    // 2. Micro-animasi untuk logo (Hover Effect)
    const logoIcon = document.querySelector('.logo-icon');
    if (logoIcon) {
        logoIcon.addEventListener('mouseenter', () => {
            logoIcon.style.transform = 'scale(1.1) rotate(15deg)';
            logoIcon.style.transition = 'transform 0.3s ease';
            logoIcon.style.boxShadow = '0 0 15px var(--accent-1)';
        });
        
        logoIcon.addEventListener('mouseleave', () => {
            logoIcon.style.transform = 'scale(1) rotate(0deg)';
            logoIcon.style.boxShadow = 'none';
        });
    }
    
    // 3. Efek Mouse Tracking di Glass Card (3D Tilt effect sederhana)
    const cards = document.querySelectorAll('.glass-card');
    
    cards.forEach(card => {
        card.addEventListener('mousemove', (e) => {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left; // Posisi X kursor di dalam card
            const y = e.clientY - rect.top;  // Posisi Y kursor di dalam card
            
            // Hitung rotasi maksimal 5 derajat
            const xRotation = ((y - rect.height / 2) / rect.height) * -10; 
            const yRotation = ((x - rect.width / 2) / rect.width) * 10;
            
            card.style.transform = `perspective(1000px) rotateX(${xRotation}deg) rotateY(${yRotation}deg) translateY(-5px)`;
        });
        
        card.addEventListener('mouseleave', () => {
            // Kembalikan ke posisi awal
            card.style.transform = 'perspective(1000px) rotateX(0) rotateY(0) translateY(0)';
            card.style.transition = 'transform 0.5s ease';
        });
        
        card.addEventListener('mouseenter', () => {
            // Matikan transisi saat hover agar efek responsif mengikuti mouse
            card.style.transition = 'none';
        });
    });

    // 4. Hamburger Menu Mobile
    const hamburger = document.querySelector('.hamburger');
    const navLinks = document.querySelector('.nav-links');

    if (hamburger && navLinks) {
        hamburger.addEventListener('click', () => {
            // Toggle Nav
            navLinks.classList.toggle('mobile-active');
            
            // Burger Animation
            hamburger.classList.toggle('toggle');
        });

        // Tutup menu saat link diklik (di mobile)
        const links = document.querySelectorAll('.nav-links li');
        links.forEach(link => {
            link.addEventListener('click', () => {
                navLinks.classList.remove('mobile-active');
                hamburger.classList.remove('toggle');
            });
        });
    }
});
